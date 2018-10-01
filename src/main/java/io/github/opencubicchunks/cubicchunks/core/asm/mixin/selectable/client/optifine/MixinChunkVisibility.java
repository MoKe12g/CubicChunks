package io.github.opencubicchunks.cubicchunks.core.asm.mixin.selectable.client.optifine;

import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.world.IMinMaxHeight;
import io.github.opencubicchunks.cubicchunks.core.CubicChunksConfig;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Mixin(targets = "net.optifine.render.ChunkVisibility")
public class MixinChunkVisibility {

    /**
     * Quadrant counter
     */
    @Shadow private static int counter = 0;
    /**
     * Current max Y for quadrants already scanned in this scan.
     */
    @Shadow private static int iMaxStatic = -1;
    /**
     * Max Y after final test of all quadrants
     */
    @Shadow private static int iMaxStaticFinal = Coords.blockToCube(Integer.MAX_VALUE) - 1;

    @Shadow private static World worldLast = null;
    @Shadow private static int pcxLast = -2147483648;
    private static int pcyLast = -2147483648;
    @Shadow private static int pczLast = -2147483648;

    @Inject(method = "getMaxChunkY", at = @At("HEAD"), cancellable = true)
    private static void getMaxChunkYCC(World world, Entity viewEntity, int renderDistanceChunks, CallbackInfoReturnable<Integer> cbi) {
        if (!((ICubicWorld) world).isCubicWorld()) {
            return;
        }
        cbi.cancel();
        if (true) {
            cbi.setReturnValue(Integer.MAX_VALUE - 1);
            return;
        }
        int pcx = MathHelper.floor(viewEntity.posX) >> 4;
        int pcy = MathHelper.floor(viewEntity.posY) >> 4;
        int pcz = MathHelper.floor(viewEntity.posZ) >> 4;

        Chunk playerChunk = world.getChunkFromChunkCoords(pcx, pcz);
        int cxStart = pcx - renderDistanceChunks;
        int cxEnd = pcx + renderDistanceChunks;

        int cyStart = pcy - CubicChunksConfig.verticalCubeLoadDistance;
        int cyEnd = pcy + CubicChunksConfig.verticalCubeLoadDistance;

        int czStart = pcz - renderDistanceChunks;
        int czEnd = pcz + renderDistanceChunks;
        if (world != worldLast || pcx != pcxLast || pcy != pcyLast || pcz != pczLast) {
            counter = 0;
            iMaxStaticFinal = Coords.blockToCube(((IMinMaxHeight) world).getMaxHeight());
            worldLast = world;
            pcxLast = pcx;
            pcyLast = pcy;
            pczLast = pcz;
        }

        if (counter == 0) {
            iMaxStatic = Coords.blockToCube(Integer.MIN_VALUE) + 1;
        }

        int iMax = iMaxStatic;
        if ((counter & 1) == 0) {
            cxEnd = pcx;
        } else {
            cxStart = pcx;
        }
        if ((counter & 2) == 0) {
            cyEnd = pcy;
        } else {
            cyStart = pcy;
        }
        if ((counter & 4) == 0) {
            czEnd = pcz;
        } else {
            czStart = pcz;
        }

        for (int cx = cxStart; cx < cxEnd; ++cx) {
            for (int cz = czStart; cz < czEnd; ++cz) {
                Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
                if (chunk.isEmpty()) {
                    continue;
                }
                Iterable<? extends ICube> cubes = ((IColumn) chunk).getLoadedCubes(cyEnd, cyStart);

                for (ICube cube : cubes) {
                    ExtendedBlockStorage ebs = cube.getStorage();
                    if (ebs != null && !ebs.isEmpty()) {
                        iMax = Math.max(iMax, cube.getY());
                        // it's sorted, in reverse, so can break when the first one is found
                        break;
                    }
                    ClassInheritanceMultiMap<Entity> cimm = cube.getEntitySet();
                    if (!cimm.isEmpty() && (chunk != playerChunk || cimm.size() != 1)) {
                        iMax = Math.max(iMax, cube.getY());
                        break;
                    }
                    Map<BlockPos, TileEntity> mapTileEntities = cube.getTileEntityMap();
                    if (!mapTileEntities.isEmpty()) {
                        Set<BlockPos> keys = mapTileEntities.keySet();

                        for (BlockPos pos : keys) {
                            int i = pos.getY() >> 4;
                            if (i > iMax) {
                                iMax = i;
                            }
                        }
                    }
                }
            }
        }

        if (counter < 7) {
            iMaxStatic = iMax;
            iMax = iMaxStaticFinal;
        } else {
            iMaxStaticFinal = iMax;
            iMaxStatic = -1;
        }

        counter = (counter + 1) % 8;
        cbi.setReturnValue(iMax << 4);
    }
}
