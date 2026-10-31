package dev.sablehighaltitudetransit.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.sablehighaltitudetransit.HighAltitudeTransitManager;

/** Final boundary preventing a high-altitude loader from waking terrain. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    @Shadow
    public ServerLevel level;

    @Inject(
            method = "addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T> void sableHighAltitudeTransit$blockAutonomousTerrainTicket(
            TicketType<T> type,
            ChunkPos chunk,
            int distance,
            T owner,
            boolean forceTicks,
            CallbackInfo ci
    ) {
        if (HighAltitudeTransitManager.suppressAutonomousLoaderTicket(this.level, type, chunk)) {
            ci.cancel();
        }
    }
}
