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

package org.orecruncher.dsurround.client.handlers;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.orecruncher.dsurround.ModBase;
import org.orecruncher.dsurround.ModOptions;
import org.orecruncher.dsurround.client.sound.SoundEngine;
import org.orecruncher.dsurround.event.DiagnosticEvent;
import org.orecruncher.dsurround.lib.OutOfBandTimerEMA;
import org.orecruncher.lib.math.MathStuff;
import org.orecruncher.lib.math.TimerEMA;

import com.google.common.collect.ImmutableList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Calculates and caches predefined sets of data points for script evaluation
 * during a tick. Goal is to minimize script evaluation overhead as much as
 * possible.
 */
@SideOnly(Side.CLIENT)
public class DiagnosticHandler extends EffectHandlerBase {

	// Diagnostic strings to display in the debug HUD
	private List<String> diagnostics;
	private List<String> timings;

	// TPS status strings to display
	private List<String> serverDataReport = ImmutableList.of();

	private final List<TimerEMA> timers = new ArrayList<>();
	private final TimerEMA clientTick = new TimerEMA("Client Tick");
	private final TimerEMA lastTick = new TimerEMA("Last Tick");
	private long timeMark;
	private long lastTickMark = -1;
	private float tps = 0;

	private TimerEMA processingTimer;
	private TimerEMA diagnosticTimer;

	public DiagnosticHandler() {
		super("Diagnostics");
	}

	public void addTimer(@Nonnull final TimerEMA timer) {
		this.timers.add(timer);
		if ("Processing".equals(timer.name()))
			this.processingTimer = timer;
		else if ("Diagnostics".equals(timer.name()))
			this.diagnosticTimer = timer;
	}

	@Override
	public void process(@Nonnull final EntityPlayer player) {
		// Gather diagnostics if needed
		if (Minecraft.getMinecraft().gameSettings.showDebugInfo) {
			this.diagnostics = new ArrayList<>();
			this.timings = new ArrayList<>();
			if (ModBase.isDeveloperMode())
				this.diagnostics.add(TextFormatting.RED + "DEVELOPER MODE ENABLED");
			if (ModOptions.logging.enableDebugLogging) {
				// Gather standard diagnostics
				final DiagnosticEvent.Gather gather = new DiagnosticEvent.Gather(player.getEntityWorld(), player);
				MinecraftForge.EVENT_BUS.post(gather);
				this.diagnostics.add("");
				this.diagnostics.addAll(gather.output);

				double adjusted = this.processingTimer.getMSecs() - this.diagnosticTimer.getMSecs();

				// Collect timing information
				this.timings.add("");
				this.timings.add(TextFormatting.LIGHT_PURPLE + this.clientTick.toString());
				this.timings.add(TextFormatting.LIGHT_PURPLE + this.lastTick.toString());
				this.timings.add(TextFormatting.LIGHT_PURPLE + String.format("TPS:%7.3fms", this.tps));
				for (final TimerEMA timer : this.timers) {
					final boolean oob = timer instanceof OutOfBandTimerEMA;
					this.timings.add((oob ? TextFormatting.GRAY : TextFormatting.AQUA) + timer.toString());
					if (oob)
						adjusted += timer.getMSecs();
				}

				final String txt = String.format(TextFormatting.YELLOW + "%s:%7.3fms", "Impact", adjusted);
				this.timings.add(txt);
			}

			if (this.serverDataReport != null && !this.serverDataReport.isEmpty()) {
				this.timings.add("");
				this.timings.addAll(this.serverDataReport);
			}
		} else {
			this.diagnostics = null;
			this.timings = null;
		}

	}

	@Override
	public void onConnect() {
		this.diagnostics = null;
		this.serverDataReport = null;
	}

	@Override
	public void onDisconnect() {
		this.diagnostics = null;
		this.serverDataReport = null;
		this.timers.clear();
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void tickStart(@Nonnull final TickEvent.ClientTickEvent event) {
		if (event.phase == Phase.START) {
			this.timeMark = System.nanoTime();
			if (this.lastTickMark != -1) {
				this.lastTick.update(this.timeMark - this.lastTickMark);
				this.tps = MathStuff.clamp((float) (50F / this.lastTick.getMSecs() * 20F), 0F, 20F);
			}
			this.lastTickMark = this.timeMark;
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void worldLoad(@Nonnull final WorldEvent.Load event) {
		if (ModOptions.logging.enableDebugLogging && event.getWorld() instanceof WorldClient) {
			final IRenderHandler handler = event.getWorld().provider.getWeatherRenderer();
			ModBase.log().debug("World class     : %s", event.getWorld().getClass().getName());
			ModBase.log().debug("World Provider  : %s", event.getWorld().provider.getClass().getName());
			ModBase.log().debug("Weather Renderer: %s", handler != null ? handler.getClass().getName() : "VANILLA");
			ModBase.log().debug("Entity Renderer : %s", Minecraft.getMinecraft().entityRenderer.getClass().getName());
			ModBase.log().debug("Particle Manager: %s", Minecraft.getMinecraft().effectRenderer.getClass().getName());
			ModBase.log().debug("Music Ticker    : %s", Minecraft.getMinecraft().getMusicTicker().getClass().getName());
			ModBase.log().debug("Sound Manager   : %s", SoundEngine.instance().getSoundManager().getClass().getName());
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void tickEnd(@Nonnull final TickEvent.ClientTickEvent event) {
		if (event.phase == Phase.END)
			this.clientTick.update(System.nanoTime() - this.timeMark);
	}

	/**
	 * Hook the Forge text event to add on our diagnostics
	 */
	@SubscribeEvent
	public void onGatherText(@Nonnull final RenderGameOverlayEvent.Text event) {
		if (this.diagnostics != null && !this.diagnostics.isEmpty()) {
			event.getLeft().addAll(this.diagnostics);
		}

		if (this.timings != null && !this.timings.isEmpty()) {
			event.getRight().addAll(this.timings);
		}
	}

	public void setServerTPSReport(@Nonnull final List<String> report) {
		this.serverDataReport = report;
	}

}
