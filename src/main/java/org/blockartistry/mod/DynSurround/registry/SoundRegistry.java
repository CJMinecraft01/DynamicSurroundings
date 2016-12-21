/* This file is part of Dynamic Surroundings, licensed under the MIT License (MIT).
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

package org.blockartistry.mod.DynSurround.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.blockartistry.mod.DynSurround.ModLog;
import org.blockartistry.mod.DynSurround.ModOptions;
import gnu.trove.map.hash.TObjectFloatHashMap;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class SoundRegistry extends Registry {

	private final List<Pattern> cullSoundNamePatterns = new ArrayList<Pattern>();
	private final List<Pattern> blockSoundNamePatterns = new ArrayList<Pattern>();
	private final TObjectFloatHashMap<String> volumeControl = new TObjectFloatHashMap<String>();

	SoundRegistry() {

	}
	
	@Override
	public void init() {
		cullSoundNamePatterns.clear();
		blockSoundNamePatterns.clear();
		volumeControl.clear();

		for (final String sound : ModOptions.culledSounds) {
			try {
				cullSoundNamePatterns.add(Pattern.compile(sound));
			} catch (final Throwable ex) {
				ModLog.warn("Unable to compile pattern for culled sound '%s'", sound);
			}
		}

		for (final String sound : ModOptions.blockedSounds) {
			try {
				blockSoundNamePatterns.add(Pattern.compile(sound));
			} catch (final Throwable ex) {
				ModLog.warn("Unable to compile pattern for blocked sound '%s'", sound);
			}
		}

		for (final String volume : ModOptions.soundVolumes) {
			final String[] tokens = StringUtils.split(volume, "=");
			if (tokens.length == 2) {
				try {
					final float vol = Integer.parseInt(tokens[1]) / 100.0F;
					volumeControl.put(tokens[0], vol);
				} catch (final Throwable t) {
					ModLog.error("Unable to process sound volume entry: " + volume, t);
				}
			}
		}
	}

	@Override
	public void fini() {

	}

	public boolean isSoundCulled(@Nonnull final String sound) {
		for (final Pattern pattern : this.cullSoundNamePatterns)
			if (pattern.matcher(sound).matches())
				return true;
		return false;
	}

	public boolean isSoundBlocked(@Nonnull final String sound) {
		for (final Pattern pattern : this.blockSoundNamePatterns)
			if (pattern.matcher(sound).matches())
				return true;
		return false;
	}

	public float getVolumeScale(@Nonnull final String soundName) {
		return this.volumeControl.contains(soundName) ? this.volumeControl.get(soundName) : 1.0F;
	}

}
