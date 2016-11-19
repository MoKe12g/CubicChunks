/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.lighting;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;

import java.util.function.Consumer;

import static net.minecraft.crash.CrashReportCategory.getCoordinateInfo;

/**
 * Handles propagating light changes from blocks.
 */
public class LightPropagator {
	private LightUpdateQueue internalRelightQueue = new LightUpdateQueue();

	/**
	 * Updates light at all BlockPos in given iterable.
	 * <p>
	 * For each block in the volume, if light source is brighter than the light value there was before - it will spread
	 * light. If the light source is less bright than the current light value - the method will redo light spreading for
	 * these blocks.
	 * <p>
	 * All coords to update must be between {@code centerPos.getX/Y/Z + }{@link LightUpdateQueue#MIN_POS} and {@code
	 * centerPos.getX/Y/Z + }{@link LightUpdateQueue#MAX_POS} of centerPos (inclusive)
	 * <p>
	 * WARNING: You probably shouldn't use this method directly and use {@link LightingManager#relightMultiBlock(BlockPos,
	 * BlockPos, EnumSkyBlock)} instead
	 *
	 * @param centerPos position relative to which calculations are done. usually average position.
	 * @param coords contains all coords that need updating
	 * @param blocks block access object. Must contain all blocks within radius of 17 blocks from all coords
	 * @param type light type to update
	 * @param setLightCallback this will be called for each position where light value is changed
	 */
	public void propagateLight(BlockPos centerPos, Iterable<BlockPos> coords, ILightBlockAccess blocks, EnumSkyBlock type, Consumer<BlockPos> setLightCallback) {

		internalRelightQueue.begin(centerPos);
		try {
			// first add all decreased light values to the queue
			coords.forEach(pos -> {
				int emitted = blocks.getEmittedLight(pos, type);
				if (blocks.getLightFor(type, pos) > emitted) {
					//add the emitted value even if it's not used here - it will be used when relighting that area
					internalRelightQueue.put(pos, emitted, 0);
				}
			});
			// follow decreasing light values until it stops decreasing,
			// setting each encountered value to 0 for easy spreading
			while (internalRelightQueue.next()) {
				BlockPos pos = internalRelightQueue.getPos();

				int currentValue = blocks.getLightFor(type, pos);
				// note: min value is 0
				int lightFromNeighbors = getExpectedLight(blocks, type, pos);
				// if this is true, this blocks currently spreads light out, and has no light coming in from neighbors
				// lightFromNeighbors == currentValue-1 means that some neighbor has the same light value, or that
				// currentValue == 1 and all surrounding blocks have light 0
				// neither of these 2 cases can be ignored.
				// note that there is no need to handle case when some surrounding block has value one higher -
				// this would mean that the current block is in the light area from other block, no need to update that
				if (lightFromNeighbors <= currentValue - 1) {
					// set it to 0 and add neighbors to the queue
					blocks.setLightFor(type, pos, 0);
					setLightCallback.accept(pos);
					// add all neighbors even those already checked - the check above will fail for them
					// because currentValue-1 == -1 (already checked are set to 0)
					// and min. possible lightFromNeighbors is 0
					for (EnumFacing direction : EnumFacing.values()) {
						BlockPos offset = pos.offset(direction);
						//add the emitted value even if it's not used here - it will be used when relighting that area
						internalRelightQueue.put(offset, blocks.getEmittedLight(offset, type), 0);
					}
				}
			}

			internalRelightQueue.resetIndex();

			// then handle everything
			coords.forEach(pos -> {
				int emitted = blocks.getEmittedLight(pos, type);
				// blocks where light decreased are already added (previous run over the queue)
				if (emitted > blocks.getLightFor(type, pos)) {
					internalRelightQueue.put(pos, emitted, 0);
				}
			});
			// spread out light values
			while (internalRelightQueue.next()) {
				BlockPos pos = internalRelightQueue.getPos();
				int value = internalRelightQueue.getValue();
				if (value <= blocks.getLightFor(type, pos)) {
					// nothing to set, can't spread further
					continue;
				}
				// set this and add neighbors to the queue
				blocks.setLightFor(type, pos, value);
				setLightCallback.accept(pos);
				for (EnumFacing direction : EnumFacing.values()) {
					BlockPos nextPos = pos.offset(direction);
					internalRelightQueue.put(nextPos, getExpectedLight(blocks, type, nextPos), 0);
				}
			}
		} catch (Throwable t) {
			CrashReport report = CrashReport.makeCrashReport(t, "Updating skylight");
			CrashReportCategory category = report.makeCategory("Skylight update");
			category.setDetail("CenterLocation", () -> getCoordinateInfo(centerPos));
			int i = 0;
			for (BlockPos pos : coords) {
				category.setDetail("UpdateLocation" + i, () -> getCoordinateInfo(pos));
				i++;
			}
			throw new ReportedException(report);
		} finally {
			internalRelightQueue.end();
		}
	}

	private int getExpectedLight(ILightBlockAccess blocks, EnumSkyBlock type, BlockPos pos) {
		return Math.max(blocks.getEmittedLight(pos, type), blocks.getLightFromNeighbors(type, pos));
	}
}
