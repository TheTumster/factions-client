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

	private static long lastLog = 0;
	private static long stageAt = 0;
	private static int shotStage = -1;

	/** Uses Minecraft's own screenshot code (found by reflection so signature changes don't break the build). */
	private static void screenshot(Minecraft mc) {
		try {
			for (var m : Class.forName("net.minecraft.client.Screenshot").getMethods()) {
				var p = m.getParameterTypes();
				if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getName().equals("grab") && p.length == 3
						&& p[0] == java.io.File.class && p[2] == java.util.function.Consumer.class) {
					java.util.function.Consumer<Object> cb = msg -> FactionsClient.LOG.info("SMOKE shot saved");
					m.invoke(null, mc.gameDirectory, mc.getMainRenderTarget(), cb);
					return;
				}
			}
			FactionsClient.LOG.warn("SMOKE no Screenshot.grab(File, RenderTarget, Consumer) found");
		} catch (Throwable t) {
			FactionsClient.LOG.warn("SMOKE screenshot failed: {}", t.toString());
		}
	}

	static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (now - lastLog > 10_000) {
			lastLog = now;
			FactionsClient.LOG.info("SMOKE state overlay={} screen={}",
					mc.getOverlay() == null ? "none" : mc.getOverlay().getClass().getSimpleName(),
					mc.screen == null ? "none" : mc.screen.getClass().getName());
		}
		if (mc.getOverlay() != null) return;
		if (titleSince < 0) {
			if (mc.screen instanceof LaGTitleScreen) {
				titleSince = System.currentTimeMillis();
				FactionsClient.LOG.info("SMOKE stage title");
			}
			return;
		}
		long t = System.currentTimeMillis() - titleSince;
		if (shotStage != stage && System.currentTimeMillis() - stageAt > 8_000 && (stage > 0 || t > 8_000)) {
			shotStage = stage;
			screenshot(mc);
		}
		if (stage == 0 && t > 20_000) {
			stage = 1;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage menu");
			mc.setScreen(new LaGMenu(mc.screen));
		} else if (stage == 1 && t > 40_000) {
			stage = 2;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage menu-hud");
			ModuleRegistry.init();
			mc.setScreen(new LaGMenu(new LaGTitleScreen()));
			setCategory(ModuleRegistry.Category.HUD);
		} else if (stage == 2 && t > 60_000) {
			stage = 3;
			stageAt = System.currentTimeMillis();
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
