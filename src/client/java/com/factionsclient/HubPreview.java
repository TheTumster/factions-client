package com.factionsclient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.factionsclient.ui.LaGTitleScreen;

/**
 * CI-only (LAGCLIENT_HUBPREVIEW=<dir>): place the RelapsedMC hub (hub-spawn.txt.gz + holos.json from lag-network) in a
 * test world at night and screenshot it from the spawn point and a few other angles. Lets us judge the hub
 * the way players actually see it, without a server.
 */
final class HubPreview {
	static final int OY = 200; // hub floor height in the preview world

	/** Camera spots: x, y, z (relative to the spawn floor centre), yaw, pitch, label. */
	private static final Object[][] VIEWS = {
			{0.5, 1.0, 0.5, 180f, -4f, "spawn"},
			{0.5, 1.0, 0.5, 180f, -24f, "spawn-up"},
			{0.5, 4.0, -22.0, 180f, 0f, "gallery"},
			{0.5, 4.0, -30.0, 0f, 12f, "gallery-back"},
			{-14.0, 2.0, -12.0, 90f, 4f, "west"},
			{14.0, 2.0, -12.0, -90f, 4f, "east"},
			{0.5, 2.0, 24.0, 180f, 0f, "south"},
			{55.0, 45.0, 45.0, 135f, 30f, "aerial"},
	};

	private static int step = -1;
	private static long at = 0;
	private static long titleSince = -1;
	private static List<String> palette;
	private static List<int[]> cells;
	private static JsonArray holos;

	private HubPreview() { }

	static boolean enabled() {
		return System.getenv("LAGCLIENT_HUBPREVIEW") != null;
	}

	private static void load() {
		try {
			Path dir = Path.of(System.getenv("LAGCLIENT_HUBPREVIEW"));
			palette = new ArrayList<>();
			cells = new ArrayList<>();
			try (var r = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(dir.resolve("hub-spawn.txt.gz"))), StandardCharsets.UTF_8))) {
				for (String p : r.readLine().split("\\|")) palette.add(p);
				String line;
				while ((line = r.readLine()) != null) {
					if (line.isBlank()) continue;
					String[] s = line.split(" ");
					cells.add(new int[] {Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]), Integer.parseInt(s[3])});
				}
			}
			Path h = dir.resolve("holos.json");
			holos = Files.exists(h) ? JsonParser.parseString(Files.readString(h)).getAsJsonArray() : new JsonArray();
			FactionsClient.LOG.info("HUBPREVIEW loaded {} blocks, {} palette, {} holos", cells.size(), palette.size(), holos.size());
		} catch (Exception e) {
			FactionsClient.LOG.error("HUBPREVIEW load failed", e);
		}
	}

	static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (step == -1) {
			if (!(mc.screen instanceof LaGTitleScreen)) return;
			if (titleSince < 0) {
				titleSince = now;
				load();
			}
			if (now - titleSince < 5000) return;
			mc.options.renderDistance().set(12);
			Smoke.createWorld(mc);
			step = 0;
			at = now;
			return;
		}
		if (step == 0) {
			if (mc.player == null || mc.level == null || mc.screen != null) {
				at = now;
				return;
			}
			if (now - at < 4000) return;
			place(mc);
			mc.options.hideGui = true;
			step = 1;
			at = now;
			return;
		}
		int v = step - 1;
		if (v >= VIEWS.length) {
			if (step == VIEWS.length + 1) {
				FactionsClient.LOG.info("HUBPREVIEW done");
				step++;
			}
			return;
		}
		Object[] view = VIEWS[v];
		long dt = now - at;
		if (dt < 100) {
			cmd(mc, String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f",
					(double) view[0], OY + (double) view[1], (double) view[2], (float) view[3], (float) view[4]));
			cmd(mc, "time set 18000");
			mc.player.getAbilities().flying = true;
		}
		// first view waits longer: the whole hub's chunks are still building
		if (dt > (v == 0 ? 45_000 : 20_000)) {
			FactionsClient.LOG.info("HUBPREVIEW shot {}", view[5]);
			Smoke.screenshot(mc);
			step++;
			at = now + 1500;
		}
	}

	private static void cmd(Minecraft mc, String c) {
		var server = mc.getSingleplayerServer();
		if (server == null) return;
		server.execute(() -> {
			try {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
			} catch (Throwable t) {
				FactionsClient.LOG.warn("HUBPREVIEW command failed {}: {}", c, t.toString());
			}
		});
	}

	private static void place(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server == null || cells == null) return;
		cmd(mc, "gamemode creative @p");
		cmd(mc, "weather clear 999999");
		cmd(mc, "time set 18000");
		server.execute(() -> {
			var level = server.overworld();
			var lookup = level.registryAccess().lookupOrThrow(Registries.BLOCK);
			BlockState[] states = new BlockState[palette.size()];
			int bad = 0;
			for (int i = 0; i < states.length; i++) {
				try {
					states[i] = BlockStateParser.parseForBlock(lookup, palette.get(i), false).blockState();
				} catch (Exception e) {
					states[i] = Blocks.AIR.defaultBlockState();
					bad++;
					FactionsClient.LOG.warn("HUBPREVIEW bad block {}", palette.get(i));
				}
			}
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int[] c : cells) {
				p.set(c[0], OY + c[1], c[2]);
				level.setBlock(p, states[c[3]], 2 | 16);
			}
			FactionsClient.LOG.info("HUBPREVIEW placed {} blocks ({} unknown types)", cells.size(), bad);
			// floating text + spinning items from the hub config (plain text: the plugin's colours aren't parsed here)
			for (var e : holos) {
				JsonObject o = e.getAsJsonObject();
				JsonArray off = o.getAsJsonArray("offset");
				double x = off.get(0).getAsDouble(), y = OY + off.get(1).getAsDouble(), z = off.get(2).getAsDouble();
				float s = o.has("scale") ? o.get("scale").getAsFloat() : 1f;
				String tf = String.format(java.util.Locale.ROOT,
						"transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%sf,%sf,%sf]}", s, s, s);
				String c;
				if (o.has("item")) {
					c = String.format(java.util.Locale.ROOT, "summon item_display %.2f %.2f %.2f {item:{id:\"minecraft:%s\",count:1},billboard:\"center\",%s}",
							x, y, z, o.get("item").getAsString().toLowerCase(java.util.Locale.ROOT), tf);
				} else {
					String text = o.get("text").getAsString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
					String color = o.has("color") ? o.get("color").getAsString() : "#FFFFFF";
					c = String.format(java.util.Locale.ROOT,
							"summon text_display %.2f %.2f %.2f {text:{text:\"%s\",color:\"%s\",bold:%s},billboard:\"center\",background:0,shadow:1b,%s}",
							x, y, z, text, color, o.has("bold") && o.get("bold").getAsBoolean() ? "true" : "false", tf);
				}
				try {
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
				} catch (Throwable t) {
					FactionsClient.LOG.warn("HUBPREVIEW holo failed: {}", t.toString());
				}
			}
		});
	}
}
