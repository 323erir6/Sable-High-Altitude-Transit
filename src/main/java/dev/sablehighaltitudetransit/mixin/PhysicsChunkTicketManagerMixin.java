package dev.sablehighaltitudetransit.mixin;

import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.sablehighaltitudetransit.HighAltitudeTransitManager;

import java.util.UUID;

@Mixin(PhysicsChunkTicketManager.class)
public abstract class PhysicsChunkTicketManagerMixin {
    /** Only skips the body's terrain-ticket loop, never its actual physics tick. */
    @Redirect(method = "update", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;isRemoved()Z"),
            require = 1, allow = 1)
    private boolean sableHighAltitudeTransit$skipEmptyTerrainWork(ServerSubLevel body) {
        return body.isRemoved() || HighAltitudeTransitManager.canSkipTerrainWork(body);
    }

    @Inject(method = "inhabitChunk", at = @At("HEAD"), cancellable = true)
    private void sableHighAltitudeTransit$doNotLoadWorldChunk(
            ServerLevel level,
            DistanceManager distanceManager,
            UUID subLevelId,
            long gameTime,
            long chunkLong,
            int chunkX,
            int chunkZ,
            CallbackInfo ci
    ) {
        if (HighAltitudeTransitManager.bypassWorldChunkTicket(level, subLevelId)) {
            ci.cancel();
        }
    }
}
