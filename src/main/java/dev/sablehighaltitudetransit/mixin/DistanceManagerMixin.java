package dev.sablehighaltitudetransit.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.sablehighaltitudetransit.HighAltitudeTransitManager;

/** Final guard against any mod moving vanilla simulation tickets in transit. */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin {
    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void sableHighAltitudeTransit$blockHighAltitudePlayerTicket(
            SectionPos position,
            ServerPlayer player,
            CallbackInfo ci
    ) {
        if (HighAltitudeTransitManager.suppressPlayerWorldStreaming(player)) {
            ci.cancel();
        }
    }
}
