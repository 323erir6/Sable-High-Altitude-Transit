package dev.sablehighaltitudetransit.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.sablehighaltitudetransit.TransitPerformanceMonitor;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void sableHighAltitudeTransit$beginTick(BooleanSupplier haveTime, CallbackInfo ci) {
        TransitPerformanceMonitor.beginTick();
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void sableHighAltitudeTransit$endTick(BooleanSupplier haveTime, CallbackInfo ci) {
        TransitPerformanceMonitor.endTick();
    }
}
