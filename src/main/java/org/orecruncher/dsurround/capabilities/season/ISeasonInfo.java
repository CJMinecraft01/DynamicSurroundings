/*
 * This file is part of Dynamic Surroundings, licensed under the MIT License (MIT).
 *
 * Copyright (c) OreCruncher
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.orecruncher.dsurround.capabilities.season;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.orecruncher.dsurround.registry.biome.BiomeInfo;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public interface ISeasonInfo {

	@Nonnull
	World getWorld();

	@Nonnull
	SeasonType getSeasonType();

	@Nonnull
	SeasonType.SubType getSeasonSubType();

	@Nonnull
	String getSeasonString();

	@Nonnull
	TemperatureRating getPlayerTemperature();

	@Nonnull
	TemperatureRating getBiomeTemperature(@Nonnull final BlockPos pos);

	@Nonnull
	BlockPos getPrecipitationHeight(@Nonnull final BlockPos pos);

	float getFloatTemperature(@Nonnull final Biome biome, @Nonnull final BlockPos pos);

	float getTemperature(@Nonnull final BlockPos pos);

	boolean canWaterFreeze(@Nonnull final BlockPos pos);

	boolean showFrostBreath(@Nonnull final BlockPos pos);

	PrecipitationType getPrecipitationType(@Nonnull final BlockPos pos, @Nullable BiomeInfo biome);
}
