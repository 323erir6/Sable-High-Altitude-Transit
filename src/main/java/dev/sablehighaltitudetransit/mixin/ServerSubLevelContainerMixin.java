package dev.sablehighaltitudetransit.mixin;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.sablehighaltitudetransit.HighAltitudeTransitManager;

import java.util.Collection;

@Mixin(ServerSubLevelContainer.class)
public abstract class ServerSubLevelContainerMixin {
    @Inject(method = "collectForceLoadedSubLevels", at = @At("RETURN"), cancellable = true)
    private void sableHighAltitudeTransit$keepPhysicsBodyActive(
            CallbackInfoReturnable<Collection<ServerSubLevel>> cir
    ) {
        ServerSubLevelContainer self = (ServerSubLevelContainer) (Object) this;
        cir.setReturnValue(HighAltitudeTransitManager.includeHighAltitudeBodies(
                self,
                cir.getReturnValue()
        ));
    }
}
