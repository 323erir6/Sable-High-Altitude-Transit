package dev.sablehighaltitudetransit;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.joml.Vector3dc;

import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Makes high-altitude Sable bodies independent of Minecraft chunk loading.
 *
 * <p>Sable normally unloads a sub-level as soon as its world-space horizontal
 * footprint touches a chunk outside block-ticking range. Force-loaded
 * sub-levels bypass that test, but Sable then calls {@code inhabitChunk}, which
 * creates real Minecraft chunk tickets. The two mixins in this mod deliberately
 * split those behaviours: high-altitude bodies are considered force-loaded by
 * Sable, while their calls to {@code inhabitChunk} are suppressed.</p>
 */
public final class HighAltitudeTransitManager {
    /** User-facing altitude above which transit must always be active. */
    public static final double MINIMUM_BOTTOM_Y = 400.0;

    private static final Map<ServerLevel, Set<UUID>> ACTIVE_BYPASSES =
            new IdentityHashMap<>();
    private static final Map<ServerLevel, Set<ServerSubLevel>> ACTIVE_BODIES =
            new IdentityHashMap<>();
    private static volatile boolean ANY_TRANSIT_ACTIVE;

    private HighAltitudeTransitManager() {
    }

    public static boolean isTransitActive() {
        return ANY_TRANSIT_ACTIVE;
    }

    /**
     * Called from the Sable container mixin immediately before its physics
     * chunk manager consumes the force-loaded collection.
     */
    public static Collection<ServerSubLevel> includeHighAltitudeBodies(
            ServerSubLevelContainer container,
            Collection<ServerSubLevel> original
    ) {
        Collection<ServerSubLevel> result = new HashSet<>(original);
        Set<UUID> activeIds = new HashSet<>();
        Set<ServerSubLevel> activeBodies = new HashSet<>();

        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (!isEligible(subLevel)) {
                continue;
            }

            result.add(subLevel);
            activeIds.add(subLevel.getUniqueId());
            activeBodies.add(subLevel);
        }

        if (activeIds.isEmpty()) {
            ACTIVE_BYPASSES.remove(container.getLevel());
            ACTIVE_BODIES.remove(container.getLevel());
        } else {
            ACTIVE_BYPASSES.put(container.getLevel(), activeIds);
            ACTIVE_BODIES.put(container.getLevel(), activeBodies);
        }
        ANY_TRANSIT_ACTIVE = !ACTIVE_BODIES.isEmpty();
        return result;
    }

    /** True only during the same Sable update that classified this body. */
    public static boolean bypassWorldChunkTicket(ServerLevel level, UUID subLevelId) {
        Set<UUID> activeIds = ACTIVE_BYPASSES.get(level);
        return activeIds != null && activeIds.contains(subLevelId);
    }

    /**
     * Blocks the normal-world tickets created by this project's autonomous
     * loader when the requesting construction is already in virtual transit.
     *
     * <p>The loader normally releases its tickets itself. This guard is kept
     * at the final ServerChunkCache boundary as well, because block-entity
     * actors tick before Sable has necessarily refreshed every cached world
     * bound. One stale altitude sample was enough to load a 5x5 area, wake its
     * mobs and Distant Horizons, and make a fast rocket visibly stutter.</p>
     */
    public static boolean suppressAutonomousLoaderTicket(
            ServerLevel level,
            TicketType<?> ticketType,
            ChunkPos chunk
    ) {
        if (ticketType == null
                || !"sable_autonomous_chunk_loader:surrounding_chunks".equals(ticketType.toString())) {
            return false;
        }

        Set<ServerSubLevel> candidates = ACTIVE_BODIES.get(level);
        if (candidates != null && isNearEligibleBody(candidates, chunk)) {
            return true;
        }

        // The autonomous loader actor can tick before PhysicsChunkTicketManager
        // calls collectForceLoadedSubLevels for this server tick. Consult the
        // authoritative container in that short window instead of allowing a
        // single expensive terrain-ticket burst.
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        return container != null && isNearEligibleBody(container.getAllSubLevels(), chunk);
    }

    private static boolean isNearEligibleBody(
            Collection<? extends ServerSubLevel> bodies,
            ChunkPos ticketChunk
    ) {
        final int maximumLoaderRadius = 8;
        for (ServerSubLevel body : bodies) {
            if (!isEligible(body)) {
                continue;
            }

            Vector3dc current = body.logicalPose().position();
            Vector3dc previous = body.lastPose().position();
            if (nearChunk(current, ticketChunk, maximumLoaderRadius)
                    || nearChunk(previous, ticketChunk, maximumLoaderRadius)) {
                return true;
            }

            BoundingBox3dc bounds = body.boundingBox();
            if (bounds == null) {
                continue;
            }
            int minX = ((int) Math.floor(bounds.minX())) >> 4;
            int maxX = ((int) Math.floor(bounds.maxX())) >> 4;
            int minZ = ((int) Math.floor(bounds.minZ())) >> 4;
            int maxZ = ((int) Math.floor(bounds.maxZ())) >> 4;
            if (ticketChunk.x >= minX - maximumLoaderRadius
                    && ticketChunk.x <= maxX + maximumLoaderRadius
                    && ticketChunk.z >= minZ - maximumLoaderRadius
                    && ticketChunk.z <= maxZ + maximumLoaderRadius) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearChunk(Vector3dc position, ChunkPos chunk, int radius) {
        if (position == null
                || !Double.isFinite(position.x())
                || !Double.isFinite(position.z())) {
            return false;
        }
        int bodyChunkX = ((int) Math.floor(position.x())) >> 4;
        int bodyChunkZ = ((int) Math.floor(position.z())) >> 4;
        return Math.abs((long) bodyChunkX - chunk.x) <= radius
                && Math.abs((long) bodyChunkZ - chunk.z) <= radius;
    }


    /**
     * Prevents an onboard player from dragging vanilla terrain generation
     * behind a fast high-altitude body. Sable's own tracking and physics are
     * separate and continue to run normally.
     */
    public static boolean suppressPlayerWorldStreaming(ServerPlayer player) {
        SubLevel tracked = Sable.HELPER.getTrackingOrVehicleSubLevel(player);
        if (tracked instanceof ServerSubLevel serverSubLevel && isEligible(serverSubLevel)) {
            return true;
        }

        // Tracking can be absent for one tick while crossing a plot boundary.
        // Keep the bypass continuous when the player is still immediately on
        // or inside a currently active high-altitude construction.
        Set<UUID> activeIds = ACTIVE_BYPASSES.get(player.serverLevel());
        if (activeIds == null || activeIds.isEmpty()) {
            return false;
        }

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(player.serverLevel());
        if (container == null) {
            return false;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        for (ServerSubLevel candidate : container.getAllSubLevels()) {
            if (!activeIds.contains(candidate.getUniqueId()) || !isEligible(candidate)) {
                continue;
            }

            BoundingBox3dc bounds = calculateCurrentBounds(candidate);
            BoundingBox3dc previousBounds = calculateBounds(candidate, candidate.lastPose());
            if (containsExpanded(bounds, x, y, z, 4.0, 3.0, 6.0)
                    || containsExpanded(previousBounds, x, y, z, 4.0, 3.0, 6.0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The ticket manager predicts at most 20 blocks vertically and adds a
     * one-block collision margin. Skip its whole per-chunk loop only if both
     * endpoint poses clear the dimension's actual build ceiling plus that
     * margin. Entity/body collision is performed by the physics pipeline,
     * outside this terrain residency loop.
     */
    public static boolean canSkipTerrainWork(ServerSubLevel body) {
        if (body == null || body.isRemoved()) {
            return false;
        }
        double floor = Math.max(MINIMUM_BOTTOM_Y, body.getLevel().getMaxBuildHeight() + 21.0);
        BoundingBox3dc current = calculateCurrentBounds(body);
        BoundingBox3dc previous = calculateBounds(body, body.lastPose());
        boolean skip = current != null && previous != null
                && Double.isFinite(current.minY()) && Double.isFinite(previous.minY())
                && current.minY() >= floor && previous.minY() >= floor;
        if (skip) TransitPerformanceMonitor.terrainLoopSkipped();
        return skip;
    }

    private static boolean containsExpanded(
            BoundingBox3dc bounds,
            double x,
            double y,
            double z,
            double horizontalMargin,
            double lowerMargin,
            double upperMargin
    ) {
        return bounds != null
                && x >= bounds.minX() - horizontalMargin
                && x <= bounds.maxX() + horizontalMargin
                && y >= bounds.minY() - lowerMargin
                && y <= bounds.maxY() + upperMargin
                && z >= bounds.minZ() - horizontalMargin
                && z <= bounds.maxZ() + horizontalMargin;
    }

    /**
     * Missing-region reads normally instantiate an empty RegionFile and leave
     * an empty .mca on disk. Fast transit used to create hundreds of these and
     * constantly churn RegionFileStorage's 256-entry cache. A missing file is
     * guaranteed to read as an absent chunk, so it is safe to return that
     * result directly while transit is active.
     */
    public static boolean skipMissingRegionRead(ChunkPos ignoredChunk) {
        // Region IO runs off-thread. Do not iterate the server-thread-owned
        // body maps here; the volatile latch is the complete information this
        // optimization needs.
        return ANY_TRANSIT_ACTIVE;
    }

    /**
     * Keeps the high-altitude construction carrying the player present on the
     * client even when the player briefly falls outside Sable's normal
     * distance check during a high-speed movement/network update. This is
     * intentionally limited to the one body whose pose was supplied to
     * Sable's tracking check; it does not increase the global tracking range.
     */
    public static boolean keepOnboardBodyTracked(Player player, Vector3dc bodyPosition) {
        if (!(player instanceof ServerPlayer serverPlayer) || bodyPosition == null) {
            return false;
        }

        SubLevel tracked = Sable.HELPER.getTrackingOrVehicleSubLevel(serverPlayer);
        if (tracked instanceof ServerSubLevel body
                && preventAutomaticUnload(body)
                && sameBodyPosition(body, bodyPosition)) {
            return true;
        }

        Set<ServerSubLevel> bodies = ACTIVE_BODIES.get(serverPlayer.serverLevel());
        if (bodies == null || bodies.isEmpty()) {
            return false;
        }

        double x = serverPlayer.getX();
        double y = serverPlayer.getY();
        double z = serverPlayer.getZ();
        for (ServerSubLevel body : bodies) {
            if (!preventAutomaticUnload(body) || !sameBodyPosition(body, bodyPosition)) {
                continue;
            }

            BoundingBox3dc bounds = body.boundingBox();
            if (bounds != null
                    && x >= bounds.minX() - 16.0 && x <= bounds.maxX() + 16.0
                    && y >= bounds.minY() - 16.0 && y <= bounds.maxY() + 16.0
                    && z >= bounds.minZ() - 16.0 && z <= bounds.maxZ() + 16.0) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameBodyPosition(ServerSubLevel body, Vector3dc bodyPosition) {
        Vector3dc pose = body.logicalPose().position();
        return pose != null && pose.distanceSquared(bodyPosition) < 0.000001;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_BYPASSES.clear();
        ACTIVE_BODIES.clear();
        ANY_TRANSIT_ACTIVE = false;
    }

    public static boolean isEligible(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) {
            return false;
        }

        double transitFloor = transitFloor(subLevel.getLevel());
        double currentPoseY = subLevel.logicalPose().position().y();
        double lastPoseY = subLevel.lastPose().position().y();

        // Rebuild the current world AABB directly from plot-local bounds and
        // the authoritative physics pose. Sable's cached global AABB can trail
        // the pose at extreme velocity; using the pose alone would be unsafe
        // for a long vertical rocket whose tail is still below the ceiling.
        BoundingBox3dc currentBounds = calculateCurrentBounds(subLevel);
        if (currentBounds != null
                && Double.isFinite(currentBounds.minY())
                && currentBounds.minY() >= transitFloor) {
            return true;
        }

        BoundingBox3dc bounds = subLevel.boundingBox();
        if (bounds != null && Double.isFinite(bounds.minY())) {
            if (bounds.minY() >= transitFloor) {
                return true;
            }

            // At high speed Sable's world AABB can still describe the last
            // completed pose while logicalPose already contains the new one.
            // Without compensating for that one-tick offset, the normal chunk
            // manager can synchronously generate hundreds of chunks before
            // the next classification pass. Translate the old bottom by the
            // exact vertical pose delta; this is the same body, not a guessed
            // look-ahead trajectory.
            double poseDeltaY = currentPoseY - lastPoseY;
            return Double.isFinite(poseDeltaY)
                    && poseDeltaY > 0.0
                    && bounds.minY() + poseDeltaY >= transitFloor;
        }

        // While a fast body crosses the edge of generated terrain, Sable may
        // briefly expose a stale/invalid global bounding box before it updates
        // the logical pose. Falling back to the body's actual pose keeps the
        // transit bypass continuous instead of dropping it for one fatal tick.
        double poseY = subLevel.logicalPose().position().y();
        return Double.isFinite(poseY) && poseY >= transitFloor;
    }

    private static BoundingBox3dc calculateCurrentBounds(ServerSubLevel subLevel) {
        return calculateBounds(subLevel, subLevel.logicalPose());
    }

    private static BoundingBox3dc calculateBounds(
            ServerSubLevel subLevel,
            dev.ryanhcode.sable.companion.math.Pose3dc pose
    ) {
        BoundingBox3ic local = subLevel.getPlot().getBoundingBox();
        if (local == null || pose == null) {
            return null;
        }

        return new BoundingBox3d(
                local.minX(), local.minY(), local.minZ(),
                local.maxX() + 1.0, local.maxY() + 1.0, local.maxZ() + 1.0
        ).transform(pose);
    }

    /**
     * There can be no vanilla terrain at or above Level#getMaxBuildHeight.
     * Starting virtual transit there avoids generating provably empty chunks
     * during the final part of an ascent. Worlds whose build ceiling is above
     * Y 400 still retain the requested Y-400 threshold.
     */
    private static double transitFloor(ServerLevel level) {
        return Math.min(MINIMUM_BOTTOM_Y, level.getMaxBuildHeight());
    }

    /**
     * Protection used by the final unload hook. A body classified during the
     * current physics pass stays protected even if its bounds are transiently
     * invalid while crossing an ungenerated chunk boundary.
     */
    public static boolean preventAutomaticUnload(ServerSubLevel subLevel) {
        if (isEligible(subLevel)) {
            return true;
        }

        Set<UUID> activeIds = ACTIVE_BYPASSES.get(subLevel.getLevel());
        return activeIds != null && activeIds.contains(subLevel.getUniqueId());
    }
}
