package dev.sablehighaltitudetransit.mixin;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.sablehighaltitudetransit.TransitPerformanceMonitor;

/**
 * Restore the vanilla height-before-chunk ordering for empty high-altitude
 * terrain. Lithium's inline_block_access overwrite obtains a LevelChunk before
 * testing its section index. An onboard player's isInWall and a thruster's
 * raycast can consequently generate terrain thousands of blocks below them.
 *
 * <p>Apply after default-priority overwrites. This is not a missing-chunk air
 * fallback: within the dimension's build range all reads remain unchanged,
 * whether the chunk exists or not. Sable's entire reserved plot grid is also
 * excluded so transformed ship rays keep reading the actual ship blocks.</p>
 */
@Mixin(value = Level.class, priority = 900)
public abstract class LevelMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void sableHighAltitudeTransit$emptySkyBeforeBlockChunk(
            BlockPos pos, CallbackInfoReturnable<BlockState> cir
    ) {
        if (sableHighAltitudeTransit$isEmptyTerrainSky(pos)) {
            TransitPerformanceMonitor.emptySkyReadSkipped();
            cir.setReturnValue(Blocks.VOID_AIR.defaultBlockState());
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void sableHighAltitudeTransit$emptySkyBeforeFluidChunk(
            BlockPos pos, CallbackInfoReturnable<FluidState> cir
    ) {
        if (sableHighAltitudeTransit$isEmptyTerrainSky(pos)) {
            TransitPerformanceMonitor.emptySkyReadSkipped();
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }

    @Unique
    private boolean sableHighAltitudeTransit$isEmptyTerrainSky(BlockPos pos) {
        Level level = (Level) (Object) this;
        return level instanceof ServerLevel
                && pos.getY() >= level.getMaxBuildHeight()
                && !Sable.HELPER.isInPlotGrid(level, pos.getX() >> 4, pos.getZ() >> 4);
    }
}
