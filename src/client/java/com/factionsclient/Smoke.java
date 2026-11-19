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
		} else if (stage == 3 && t > 80_000) {
			stage = 4;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage multiplayer");
			var list = new net.minecraft.client.multiplayer.ServerList(mc);
			list.load();
			boolean pinned = list.size() > 0 && com.factionsclient.RelapsedServer.isRelapsed(list.get(0).ip);
			FactionsClient.LOG.info("SMOKE pinned={}", pinned);
			// Modes: Skyblock turns factions overlays off, Factions turns them back on, a tweak sticks to its mode.
			var c = FactionsClient.CONFIG;
			com.factionsclient.Modes.set(com.factionsclient.Modes.Mode.SKYBLOCK, false);
			boolean skyOk = !c.crumbs && !c.claimMap && c.hudCoords;
			c.hudCoords = false; // player tweak while in Skyblock
			com.factionsclient.Modes.set(com.factionsclient.Modes.Mode.FACTIONS, false);
			boolean facOk = c.crumbs && c.claimMap && c.team && c.hudCoords;
			com.factionsclient.Modes.set(com.factionsclient.Modes.Mode.SKYBLOCK, false);
			boolean kept = !c.hudCoords;
			com.factionsclient.Modes.set(com.factionsclient.Modes.Mode.FACTIONS, false);
			ModuleRegistry.init();
			java.util.Map<String, Integer> pages = new java.util.LinkedHashMap<>();
			for (var m : ModuleRegistry.ALL) {
				if (m.name.equals("Printer") || m.name.equals("Minimap") || m.name.equals("Video & Performance") || m.name.equals("Litematica: General"))
					pages.put(m.name, m.settings.get().size());
			}
			boolean bridges = pages.size() == 4 && pages.values().stream().allMatch(n -> n >= 3) && pages.get("Printer") < 30; // printer: only its own options
			FactionsClient.LOG.info("SMOKE bridges {} {}", bridges ? "ok" : "BROKEN", pages);
			FactionsClient.LOG.info("SMOKE modes={} (sky={} fac={} kept={})", skyOk && facOk && kept ? "ok" : "BROKEN", skyOk, facOk, kept);
			mc.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new LaGTitleScreen()));
		} else if (stage == 4 && t > 100_000) {
			stage = 5;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage menu-factions");
			ModuleRegistry.init();
			mc.setScreen(new LaGMenu(new LaGTitleScreen()));
			setCategory(ModuleRegistry.Category.FACTIONS);
		} else if (stage == 5 && t > 120_000) {
			stage = 6;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage hud-editor");
			mc.setScreen(new com.factionsclient.HudEditorScreen(new LaGTitleScreen()));
		} else if (stage == 6 && t > 140_000) {
			stage = 7;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage menu-small");
			// Small window: the menu must still fit and not cut names off.
			mc.options.guiScale().set(4);
			mc.resizeDisplay();
			ModuleRegistry.init();
			mc.setScreen(new LaGMenu(new LaGTitleScreen()));
			setCategory(ModuleRegistry.Category.HUD);
		} else if (stage == 7 && t > 160_000) {
			stage = 8;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage world-create");
			mc.options.guiScale().set(0);
			mc.resizeDisplay();
			createWorld(mc);
		} else if (stage == 8) {
			// Wait for the world to actually be playable
			if (mc.player == null || mc.level == null || mc.screen != null) {
				loadedAt = 0;
				return;
			}
			if (loadedAt == 0) loadedAt = System.currentTimeMillis();
			if (System.currentTimeMillis() - loadedAt < 6_000) return;
			stage = 9;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage ingame");
			setUpScene(mc);
		} else if (stage == 9 && !chatCleared && System.currentTimeMillis() - stageAt > 4_000) {
			chatCleared = true; // command feedback arrives late: clear it before the screenshot
			mc.gui.getChat().clearMessages(false);
		} else if (stage == 9 && System.currentTimeMillis() - stageAt > 14_000) {
			FactionsClient.LOG.info("SMOKE shots traced={} cannons={} {}", ShotTracer.SHOTS.size(), ShotTracer.CANNONS.size(),
					ShotTracer.CANNONS.isEmpty() ? "" : ShotTracer.CANNONS.get(0).pos + " shots=" + ShotTracer.CANNONS.get(0).shots
							+ " player=" + mc.player.position());
			stage = 10;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage ingame-hud-editor");
			mc.setScreen(new com.factionsclient.HudEditorScreen(null));
		} else if (stage == 10 && System.currentTimeMillis() - stageAt > 12_000) {
			stage = 11;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage ingame-menu");
			ModuleRegistry.init();
			mc.setScreen(new LaGMenu(null));
			setCategory(ModuleRegistry.Category.FACTIONS);
		} else if (stage == 11 && System.currentTimeMillis() - stageAt > 12_000) {
			stage = 12;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage printer-page");
			var menu = new LaGMenu(null);
			mc.setScreen(menu);
			menu.openPage("Printer");
		} else if (stage == 12 && System.currentTimeMillis() - stageAt > 12_000) {
			stage = 13;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage minimap-page");
			var menu = new LaGMenu(null);
			mc.setScreen(menu);
			menu.openPage("Minimap");
		} else if (stage == 13 && System.currentTimeMillis() - stageAt > 12_000) {
			stage = 14;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage video-page");
			var menu = new LaGMenu(null);
			mc.setScreen(menu);
			menu.openPage("Video & Performance");
		} else if (stage == 14 && System.currentTimeMillis() - stageAt > 12_000) {
			stage = 15;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage chat-tabs");
			TeamLink.PINGS.clear();
			mc.setScreen(null);
			var chat = mc.gui.getChat();
			chat.clearMessages(false);
			String[] lines = {
					"Welcome to RelapsedMC! Type /help to get started.",
					"Steve » anyone selling sand?",
					"[faction-only] *Relapse <Teammate>: walls getting hit on the east side",
					"Alex » wts 10k sand dm me",
					"[ally-only] Allies <Builder>: need tnt?",
					"Teammate whispers to you: hop on discord",
					"[faction-only] *Relapse <Teammate>: patching now",
					"Server restarting in 5 minutes",
					"Server restarting in 5 minutes",
					"Server restarting in 5 minutes"};
			for (String l : lines) chat.addMessage(net.minecraft.network.chat.Component.literal(l));
			mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
			ChatTabs.selectByName("Faction");
			int shown = 0;
			for (String l : lines) if (ChatTabs.visible(net.minecraft.network.chat.Component.literal(l))) shown++;
			FactionsClient.LOG.info("SMOKE chattabs {} (faction tab shows {} of {})", shown == 2 ? "ok" : "BROKEN", shown, lines.length);
		} else if (stage == 15 && System.currentTimeMillis() - stageAt > 12_000) {
			stage = 16;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage team-page");
			var menu = new LaGMenu(null);
			mc.setScreen(menu);
			menu.openPage("Your Team");
		} else if (stage == 16 && System.currentTimeMillis() - stageAt > 12_000) {
			stage = 17;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage team-schems");
			var menu = new LaGMenu(null);
			mc.setScreen(menu);
			menu.openPage("Team Schematics");
		} else if (stage == 17 && System.currentTimeMillis() - stageAt > 12_000) {
			stage = 18;
			stageAt = System.currentTimeMillis();
			FactionsClient.LOG.info("SMOKE stage motion-blur");
			mc.setScreen(null);
			FactionsClient.CONFIG.motionBlur = true;
			FactionsClient.CONFIG.motionBlurStrength = 8;
		} else if (stage == 18 && System.currentTimeMillis() - stageAt <= 12_000) {
			if (mc.player != null) mc.player.setYRot(mc.player.getYRot() + 6f);   // keep turning so the trail shows
		} else if (stage == 18) {
			stage = 19;
			FactionsClient.CONFIG.motionBlur = false;
			FactionsClient.LOG.info("SMOKE shots traced={} cannons={} {}", ShotTracer.SHOTS.size(), ShotTracer.CANNONS.size(),
					ShotTracer.CANNONS.isEmpty() ? "" : ShotTracer.CANNONS.get(0).pos + " shots=" + ShotTracer.CANNONS.get(0).shots
							+ " player=" + mc.player.position());
			FactionsClient.LOG.info("SMOKE stage done");
		}
	}

	private static long loadedAt = 0;
	private static boolean chatCleared = false;

	/** Creative world, like vanilla's debug world button. */
	private static void createWorld(Minecraft mc) {
		try {
			var settings = new net.minecraft.world.level.LevelSettings("SMOKE world", net.minecraft.world.level.GameType.CREATIVE,
					false, net.minecraft.world.Difficulty.PEACEFUL, true,
					new net.minecraft.world.level.gamerules.GameRules(net.minecraft.world.level.WorldDataConfiguration.DEFAULT.enabledFeatures()),
					net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
			mc.createWorldOpenFlows().createFreshLevel("SMOKE world", settings,
					net.minecraft.client.gui.screens.worldselection.SelectWorldScreen.TEST_OPTIONS,
					net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, mc.screen);
		} catch (Throwable e) {
			FactionsClient.LOG.warn("SMOKE world create failed: {}", e.toString());
		}
	}

	/**
	 * Pretend we're on RelapsedMC Factions: claims around us (own/ally/enemy/safezone), a teammate, an /f home,
	 * and some live TNT for crumb trails, so one screenshot shows every overlay.
	 */
	private static void setUpScene(Minecraft mc) {
		com.factionsclient.Modes.set(com.factionsclient.Modes.Mode.FACTIONS, false);
		var c = FactionsClient.CONFIG;
		c.crumbs = true;
		c.claimMap = true;
		c.homeBeacon = true;
		c.team = true;
		c.hud = true;
		int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
		java.util.Map<Long, ServerLink.Claim> claims = new java.util.HashMap<>();
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
			claims.put(ServerLink.chunkKey(pcx + dx, pcz + dz), new ServerLink.Claim(ServerLink.OWN, "Relapse"));
		for (int dz = -1; dz <= 1; dz++) claims.put(ServerLink.chunkKey(pcx + 3, pcz + dz), new ServerLink.Claim(ServerLink.ENEMY, "Ravens"));
		for (int dx = -1; dx <= 0; dx++) claims.put(ServerLink.chunkKey(pcx + dx, pcz - 3), new ServerLink.Claim(ServerLink.ALLY, "Allies"));
		claims.put(ServerLink.chunkKey(pcx - 3, pcz + 1), new ServerLink.Claim(ServerLink.SAFEZONE, "SafeZone"));
		ServerLink.CLAIMS = claims;
		ServerLink.CLAIMS_DIM = ServerLink.dimId(mc);
		ServerLink.REALM = "factions";
		ServerLink.connected = true;
		var pos = mc.player.position();
		ServerLink.HOME = new ServerLink.Home(ServerLink.dimId(mc), pos.x + 70, pos.y, pos.z + 22);
		ServerLink.MATES = java.util.List.of(new ServerLink.Mate("Teammate", pos.x + 40, pos.y, pos.z - 18, ServerLink.dimId(mc)));
		mc.player.getAbilities().flying = true;
		mc.player.onUpdateAbilities();
		mc.gui.getChat().clearMessages(false);
		ServerLink.lastMatesForSmoke();
		// Pretend we're in a RelapsedMC team too: a pink teammate, an enemy ping, a shared schematic
		TeamLink.connected = true;
		TeamLink.team = "Relapse";
		TeamLink.leader = mc.getUser().getName();
		TeamLink.youLead = true;
		TeamLink.members = java.util.List.of(new TeamLink.Member(mc.getUser().getName(), true, "factions"),
				new TeamLink.Member("Builder", true, "skyblock"), new TeamLink.Member("Scout", false, ""));
		TeamLink.schems = java.util.List.of(new TeamLink.Schem(7, "east-wall.litematic", "Builder", 48_213, System.currentTimeMillis()));
		TeamLink.MATES = java.util.List.of(new ServerLink.Mate("Scout", pos.x + 30, pos.y, pos.z + 25, ServerLink.dimId(mc)));
		TeamLink.lastMatesForSmoke();
		TeamLink.PINGS.add(new TeamLink.Ping("Scout", "enemy", ServerLink.dimId(mc), pos.x + 26, pos.y + 2, pos.z - 6, System.currentTimeMillis()));
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> {
			var src = server.createCommandSourceStack();
			String[] cmds = {"time set day", "weather clear",
					"execute at @p run fill ~-1 ~23 ~-1 ~1 ~23 ~1 minecraft:glass",
					"execute as @a at @s run tp @s ~ ~24 ~ -70 28",
					"execute at @p run summon tnt ~14 ~-6 ~4 {fuse:300,Motion:[0.0,0.35,0.05]}",
					"execute at @p run summon tnt ~18 ~-8 ~-3 {fuse:300,Motion:[0.04,0.4,0.0]}",
					"execute at @p run summon tnt ~22 ~-10 ~1 {fuse:300,Motion:[-0.03,0.45,0.02]}",
					// a "cannon" barrel 45 blocks out, firing a shot back toward us (Shot Tracer should find the barrel)
					"execute at @p run fill ~45 ~-6 ~9 ~47 ~-4 ~11 minecraft:obsidian",
					"execute at @p run summon tnt ~44 ~-5 ~10 {fuse:60,Motion:[-1.9,0.3,-0.2]}",
					"execute at @p run summon falling_block ~44 ~-5 ~10.4 {BlockState:{Name:\"minecraft:sand\"},Time:1,Motion:[-1.8,0.3,-0.2]}"};
			for (String cmd : cmds) {
				try {
					server.getCommands().performPrefixedCommand(src, cmd);
				} catch (Throwable e) {
					FactionsClient.LOG.warn("SMOKE command failed {}: {}", cmd, e.toString());
				}
			}
		});
		FactionsClient.LOG.info("SMOKE scene ready mode={} claims={}", com.factionsclient.Modes.current(), claims.size());
	}

	private static void setCategory(ModuleRegistry.Category c) {
		try {
			var f = LaGMenu.class.getDeclaredField("category");
			f.setAccessible(true);
			f.set(null, c);
		} catch (Exception ignored) { }
	}
}
