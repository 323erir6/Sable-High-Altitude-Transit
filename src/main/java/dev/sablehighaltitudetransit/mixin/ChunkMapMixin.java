package dev.sablehighaltitudetransit.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.sablehighaltitudetransit.HighAltitudeTransitManager;

/**
 * Freezes only vanilla terrain streaming while a player is aboard a Sable
 * body above the transit altitude. Entity tracking at the beginning of
 * ChunkMap#move is deliberately left intact.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Inject(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getLastSectionPos()Lnet/minecraft/core/SectionPos;"
            ),
            cancellable = true
    )
    private void sableHighAltitudeTransit$freezePlayerChunkTicket(
            ServerPlayer player,
            CallbackInfo ci
    ) {
        if (HighAltitudeTransitManager.suppressPlayerWorldStreaming(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void sableHighAltitudeTransit$freezeTerrainStreaming(
            ServerPlayer player,
            CallbackInfo ci
    ) {
        if (HighAltitudeTransitManager.suppressPlayerWorldStreaming(player)) {
            ci.cancel();
        }
    }
}
