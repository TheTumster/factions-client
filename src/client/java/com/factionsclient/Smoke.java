package com.factionsclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import com.factionsclient.ui.LaGMenu;
import com.factionsclient.ui.LaGTitleScreen;

/** CI-only: when LAGCLIENT_SMOKE is set, walk through our screens so the smoke test can screenshot them. */
final class Smoke {
	private static long titleSince = -1;
	private static int stage = 0;

	private Smoke() { }

	static boolean enabled() {
		return System.getenv("LAGCLIENT_SMOKE") != null;
	}

	static void tick(Minecraft mc) {
		if (mc.getOverlay() != null) return;
		if (titleSince < 0) {
			if (mc.screen instanceof LaGTitleScreen) {
				titleSince = System.currentTimeMillis();
				FactionsClient.LOG.info("SMOKE stage title");
			}
			return;
		}
		long t = System.currentTimeMillis() - titleSince;
		if (stage == 0 && t > 20_000) {
			stage = 1;
			FactionsClient.LOG.info("SMOKE stage menu");
			mc.setScreen(new LaGMenu(mc.screen));
		} else if (stage == 1 && t > 40_000) {
			stage = 2;
			FactionsClient.LOG.info("SMOKE stage menu-hud");
			ModuleRegistry.init();
			mc.setScreen(new LaGMenu(new LaGTitleScreen()));
			setCategory(ModuleRegistry.Category.HUD);
		} else if (stage == 2 && t > 60_000) {
			stage = 3;
			FactionsClient.LOG.info("SMOKE stage options");
			Screen parent = new LaGTitleScreen();
			mc.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(parent, mc.options));
		}
	}

	private static void setCategory(ModuleRegistry.Category c) {
		try {
			var f = LaGMenu.class.getDeclaredField("category");
			f.setAccessible(true);
			f.set(null, c);
		} catch (Exception ignored) { }
	}
}
