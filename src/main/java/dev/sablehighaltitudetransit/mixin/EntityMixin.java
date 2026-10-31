package dev.sablehighaltitudetransit.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import dev.sablehighaltitudetransit.HighAltitudeTransitManager;

/**
 * NeoForge appends a synchronous Level#getChunk call to Entity#setPosRaw so
 * every moved entity has a materialized destination chunk. Sable moves an
 * onboard player every physics tick, which would otherwise generate terrain
 * along the complete high-speed flight path even after player chunk tickets
 * have been suppressed.
 *
 * <p>The return value of this particular call is discarded. During transit we
 * may therefore return an already-loaded chunk, or null when it is absent,
 * without changing player position, Sable collision, tracking or controls.</p>
 */
@Mixin(value = Entity.class, priority = 1200)
public abstract class EntityMixin {
    @Redirect(
            method = "setPosRaw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"
            )
    )
    private LevelChunk sableHighAltitudeTransit$doNotGeneratePlayerDestinationChunk(
            Level level,
            int chunkX,
            int chunkZ
    ) {
        Object self = this;
        if (self instanceof ServerPlayer player
                && level instanceof ServerLevel serverLevel
                && HighAltitudeTransitManager.suppressPlayerWorldStreaming(player)) {
            return serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
        }

        return level.getChunk(chunkX, chunkZ);
    }
}
