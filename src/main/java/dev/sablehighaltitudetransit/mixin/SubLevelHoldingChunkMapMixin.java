package dev.sablehighaltitudetransit.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.sablehighaltitudetransit.HighAltitudeTransitManager;

/** Final safety net: never serialize and remove an active transit body merely
 * because the normal-world chunk below it does not exist. */
@Mixin(value = SubLevelHoldingChunkMap.class, priority = 2000)
public abstract class SubLevelHoldingChunkMapMixin {
    @Inject(method = "moveToUnloaded", at = @At("HEAD"), cancellable = true)
    private void sableHighAltitudeTransit$keepBodyInPhysics(
            ServerSubLevel subLevel,
            ChunkPos unloadedChunk,
            CallbackInfo ci
    ) {
        if (HighAltitudeTransitManager.preventAutomaticUnload(subLevel)) {
            ci.cancel();
        }
    }
}
