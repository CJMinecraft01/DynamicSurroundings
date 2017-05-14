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

package org.blockartistry.DynSurround.client.footsteps.implem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.blockartistry.DynSurround.DSurround;
import org.blockartistry.DynSurround.api.events.FootstepEvent;
import org.blockartistry.DynSurround.client.footsteps.interfaces.EventType;
import org.blockartistry.DynSurround.client.footsteps.interfaces.IAcoustic;
import org.blockartistry.DynSurround.client.footsteps.interfaces.IOptions;
import org.blockartistry.DynSurround.client.footsteps.interfaces.ISoundPlayer;
import org.blockartistry.DynSurround.client.footsteps.interfaces.IStepPlayer;
import org.blockartistry.DynSurround.client.footsteps.interfaces.IOptions.Option;
import org.blockartistry.DynSurround.client.footsteps.system.Association;
import org.blockartistry.DynSurround.client.footsteps.system.Isolator;
import org.blockartistry.DynSurround.client.handlers.EnvironStateHandler.EnvironState;
import org.blockartistry.DynSurround.client.sound.FootstepSound;
import org.blockartistry.DynSurround.client.sound.SoundEngine;
import org.blockartistry.DynSurround.network.Network;
import org.blockartistry.DynSurround.network.PacketGenerateFootstep;
import org.blockartistry.lib.BlockPosHelper;
import org.blockartistry.lib.MCHelper;
import org.blockartistry.lib.TimeUtils;
import org.blockartistry.lib.WorldUtils;
import org.blockartistry.lib.random.XorShiftRandom;

import net.minecraft.block.SoundType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A ILibrary that can also play sounds and default footsteps.
 */
@SideOnly(Side.CLIENT)
public class AcousticsManager implements ISoundPlayer, IStepPlayer {

	private final Random RANDOM = XorShiftRandom.current();

	private final HashMap<String, IAcoustic> acoustics = new HashMap<String, IAcoustic>();
	private final ArrayDeque<PendingSound> pending = new ArrayDeque<PendingSound>();
	private final Isolator isolator;
	private final BlockPos.MutableBlockPos stepCheck = new BlockPos.MutableBlockPos();

	// Special sentinels for equating
	public static final IAcoustic[] EMPTY = {};
	public static final IAcoustic[] NOT_EMITTER = { new BasicAcoustic("NOT_EMITTER") };
	public static final IAcoustic[] MESSY_GROUND = { new BasicAcoustic("MESSY_GROUND") };
	public static IAcoustic[] SWIM;

	public AcousticsManager(@Nonnull final Isolator isolator) {
		this.isolator = isolator;
	}

	public void addAcoustic(@Nonnull final IAcoustic acoustic) {
		this.acoustics.put(acoustic.getAcousticName(), acoustic);
	}

	@Nullable
	public IAcoustic getAcoustic(@Nonnull final String name) {
		return this.acoustics.get(name);
	}

	protected void produceFootprint(final int dim, @Nonnull final Vec3d pos, final float rotation,
			final boolean rightFoot) {
		if (DSurround.isInstalledOnServer()) {
			final PacketGenerateFootstep packet = new PacketGenerateFootstep(dim, pos, rotation, rightFoot);
			Network.sendToServer(packet);
		} else {
			final FootstepEvent.Display event = new FootstepEvent.Display(dim, pos, rotation, rightFoot);
			MinecraftForge.EVENT_BUS.post(event);
		}
	}

	public void playAcoustic(@Nonnull final Object location, @Nonnull final Association acousticName,
			@Nonnull final EventType event) {
		playAcoustic(location, acousticName.getData(), event, null);

		if (acousticName.hasFootstepImprint()) {
			final Vec3d stepLoc = acousticName.getStepLocation();
			if (stepLoc != null && WorldUtils.isSolidBlock(EnvironState.getWorld(),
					BlockPosHelper.setPos(this.stepCheck, stepLoc).move(EnumFacing.DOWN, 1))) {
				produceFootprint(EnvironState.getDimensionId(), stepLoc, acousticName.getRotation(),
						acousticName.isRightFoot());
			}
		}
	}

	private void logAcousticPlay(@Nonnull final IAcoustic[] acoustics, @Nonnull final EventType event) {
		final StringBuilder builder = new StringBuilder();
		boolean doComma = false;
		for (int i = 0; i < acoustics.length; i++) {
			if (doComma)
				builder.append(",");
			else
				doComma = true;
			builder.append(acoustics[i].getAcousticName());
		}
		DSurround.log().debug("Playing acoustic %s for event %s", builder.toString(), event.toString().toUpperCase());
	}

	public void playAcoustic(@Nonnull final Object location, @Nonnull final IAcoustic[] acoustics,
			@Nonnull final EventType event, @Nullable final IOptions inputOptions) {

		if (acoustics != null) {
			if (DSurround.log().isDebugging())
				logAcousticPlay(acoustics, event);

			for (int i = 0; i < acoustics.length; i++) {
				acoustics[i].playSound(mySoundPlayer(), location, event, inputOptions);
			}
		}
	}

	@Nonnull
	public IAcoustic[] compileAcoustics(@Nonnull final String acousticName) {
		if (acousticName.equals("NOT_EMITTER"))
			return NOT_EMITTER;
		else if (acousticName.equals("MESSY_GROUND"))
			return MESSY_GROUND;

		final List<IAcoustic> acoustics = new ArrayList<IAcoustic>();

		final String fragments[] = acousticName.split(",");
		for (final String fragment : fragments) {
			final IAcoustic acoustic = this.acoustics.get(fragment);
			if (acoustic == null) {
				DSurround.log().warn("Acoustic '%s' not found!", fragment);
			} else {
				acoustics.add(acoustic);
			}
		}

		return acoustics.size() == 0 ? EMPTY : acoustics.toArray(new IAcoustic[acoustics.size()]);
	}

	@Override
	public void playStep(@Nonnull final EntityLivingBase entity, @Nonnull final Association assos) {
		try {
			SoundType soundType = assos.getSoundType();
			if (!assos.isLiquid() && assos.getSoundType() != null) {

				if (WorldUtils.getBlockState(EnvironState.getWorld(), assos.getPos().up())
						.getBlock() == Blocks.SNOW_LAYER) {
					soundType = MCHelper.getSoundType(Blocks.SNOW_LAYER);
				}

				actuallyPlaySound(entity, soundType.getStepSound(), soundType.getVolume() * 0.15F,
						soundType.getPitch());
			}
		} catch (final Throwable t) {
			DSurround.log().error("Unable to play step sound", t);
		}
	}

	@Override
	public void playSound(@Nonnull final Object location, @Nonnull final SoundEvent sound, final float volume,
			final float pitch, @Nullable final IOptions options) {
		if (!(location instanceof Entity))
			return;

		try {
			if (options != null) {
				if (options.hasOption(Option.DELAY_MIN) && options.hasOption(Option.DELAY_MAX)) {
					final long delay = TimeUtils.currentTimeMillis()
							+ randAB(RANDOM, options.asLong(Option.DELAY_MIN), options.asLong(Option.DELAY_MAX));
					this.pending.add(new PendingSound(location, sound, volume, pitch, null, delay,
							options.asLong(Option.DELAY_MAX)));
				} else {
					actuallyPlaySound((Entity) location, sound, volume, pitch);
				}
			} else {
				actuallyPlaySound((Entity) location, sound, volume, pitch);
			}
		} catch (final Throwable t) {
			DSurround.log().error("Unable to play sound", t);
		}
	}

	protected void actuallyPlaySound(@Nonnull final Entity location, @Nonnull final SoundEvent sound,
			final float volume, final float pitch) {

		try {
			final FootstepSound s = new FootstepSound(location, sound).setVolume(volume).setPitch(pitch);
			SoundEngine.instance().playSound(s);
		} catch (final Throwable t) {
			DSurround.log().error("Unable to play sound", t);
		}
	}

	private long randAB(@Nonnull final Random rng, final long a, final long b) {
		return a >= b ? a : a + rng.nextInt((int) (b + 1));
	}

	@Override
	@Nonnull
	public Random getRNG() {
		return RANDOM;
	}

	public void think() {

		if (this.pending.isEmpty())
			return;

		final long time = TimeUtils.currentTimeMillis();

		while (this.pending.peek().getTimeToPlay() <= time) {
			final PendingSound sound = this.pending.poll();
			if (!sound.isLate(time)) {
				sound.playSound(this);
			} else if (DSurround.log().isDebugging()) {
				DSurround.log().debug("    Skipped late sound (late by " + sound.howLate(time) + "ms, tolerence is "
						+ sound.getLateTolerance() + "ms)");
			}

			if (this.pending.isEmpty())
				break;
		}
	}

	@Nonnull
	protected ISoundPlayer mySoundPlayer() {
		return isolator.getSoundPlayer();
	}
}