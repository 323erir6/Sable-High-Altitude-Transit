package dev.sablehighaltitudetransit.mixin;

import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.sablehighaltitudetransit.HighAltitudeTransitManager;

/** Keeps only the occupied transit body tracked across high-speed updates. */
@Mixin(value = SubLevelTrackingSystem.class, remap = false)
public abstract class SubLevelTrackingSystemMixin {
    @Inject(method = "shouldLoad", at = @At("HEAD"), cancellable = true)
    private void sableHighAltitudeTransit$keepOnboardBodyTracked(
            Player player,
            Vector3dc bodyPosition,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (HighAltitudeTransitManager.keepOnboardBodyTracked(player, bodyPosition)) {
            cir.setReturnValue(true);
        }
    }
}
