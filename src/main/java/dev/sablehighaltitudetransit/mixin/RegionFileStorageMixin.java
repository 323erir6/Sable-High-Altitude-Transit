package dev.sablehighaltitudetransit.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.sablehighaltitudetransit.HighAltitudeTransitManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Avoids opening/creating region files that provably do not exist. */
@Mixin(RegionFileStorage.class)
public abstract class RegionFileStorageMixin {
    @Shadow
    private Path folder;

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void sableHighAltitudeTransit$skipMissingRead(
            ChunkPos chunk,
            CallbackInfoReturnable<CompoundTag> cir
    ) {
        if (shouldSkip(chunk)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "scanChunk", at = @At("HEAD"), cancellable = true)
    private void sableHighAltitudeTransit$skipMissingScan(
            ChunkPos chunk,
            StreamTagVisitor visitor,
            CallbackInfo ci
    ) {
        if (shouldSkip(chunk)) {
            ci.cancel();
        }
    }

    private boolean shouldSkip(ChunkPos chunk) {
        if (!HighAltitudeTransitManager.skipMissingRegionRead(chunk)) {
            return false;
        }

        Path region = this.folder.resolve(
                "r." + chunk.getRegionX() + "." + chunk.getRegionZ() + ".mca"
        );
        if (!Files.exists(region)) {
            return true;
        }

        // Previous flights may already have left a zero-byte placeholder.
        // It contains no chunks and has the same read result as a missing
        // file, so avoid reopening it on every pass as well.
        try {
            return Files.size(region) == 0L;
        } catch (IOException ignored) {
            return false;
        }
    }
}
