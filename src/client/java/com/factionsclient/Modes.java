package com.factionsclient;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

/**
 * One preset per RelapsedMC realm. A mode remembers which modules and HUD pieces are on; switching saves the
 * current mode's toggles and loads the next one's. The server says which realm you're on (relapsed:realm) and
 * the client switches by itself, or players click a mode in the menu.
 */
public final class Modes {
	public enum Mode {
		FACTIONS("Factions", "⚔", "Crumbs, claim map, team tracking, home beacon, TNT count"),
		CANNONS("Cannons", "✹", "Trails + chunk borders for building and testing cannons"),
		SKYBLOCK("Skyblock", "☁", "Clean HUD: coords, effects, armor. No factions overlays"),
		SMP("SMP", "⌂", "Survival HUD: coords, effects, armor, keystrokes off"),
		HUB("Hub", "★", "Almost nothing on screen while you pick a realm"),
		GENERAL("General", "◆", "Any other server or singleplayer");

		public final String label, icon, desc;

		Mode(String label, String icon, String desc) {
			this.label = label;
			this.icon = icon;
			this.desc = desc;
		}

		/** Realm name from the server (laGCore's role) -> mode. */
		public static Mode fromRealm(String realm) {
			String r = realm == null ? "" : realm.toLowerCase(Locale.ROOT);
			if (r.contains("faction")) return FACTIONS;
			if (r.contains("cannon") || r.contains("lab")) return CANNONS;
			if (r.contains("sky")) return SKYBLOCK;
			if (r.contains("smp") || r.contains("survival")) return SMP;
			if (r.contains("hub") || r.contains("lobby")) return HUB;
			return GENERAL;
		}
	}

	/** Config fields a mode controls (everything else, like colors and HUD positions, is shared). */
	static final String[] MANAGED = {
			"crumbs", "shareCrumbs", "team", "chunkGrid", "claimMap", "homeBeacon",
			"hudInfo", "hudCoords", "hudTerritory", "hudStatus", "hudTeamList", "hudKeystrokes", "hudEffects", "hudArmor"};

	private Modes() { }

	/** Built-in starting presets. Players' own changes in a mode are saved over these. */
	static Map<String, Boolean> preset(Mode m) {
		Map<String, Boolean> p = new LinkedHashMap<>();
		for (String f : MANAGED) p.put(f, false);
		switch (m) {
			case FACTIONS -> on(p, "crumbs", "shareCrumbs", "team", "claimMap", "homeBeacon",
					"hudInfo", "hudCoords", "hudTerritory", "hudStatus", "hudTeamList", "hudKeystrokes", "hudEffects", "hudArmor");
			case CANNONS -> on(p, "crumbs", "chunkGrid", "hudInfo", "hudCoords", "hudEffects");
			case SKYBLOCK -> on(p, "hudInfo", "hudCoords", "hudEffects", "hudArmor");
			case SMP -> on(p, "hudInfo", "hudCoords", "hudStatus", "hudEffects", "hudArmor");
			case HUB -> on(p, "hudInfo");
			case GENERAL -> on(p, "crumbs", "hudInfo", "hudCoords", "hudStatus", "hudKeystrokes", "hudEffects", "hudArmor");
		}
		return p;
	}

	private static void on(Map<String, Boolean> p, String... keys) {
		for (String k : keys) p.put(k, true);
	}

	public static Mode current() {
		try {
			return Mode.valueOf(FactionsClient.CONFIG.mode);
		} catch (Exception e) {
			return Mode.GENERAL;
		}
	}

	/** Switch to a mode: save this mode's toggles, load the next one's. */
	public static void set(Mode next, boolean announce) {
		Config c = FactionsClient.CONFIG;
		Mode cur = current();
		if (c.modes == null) c.modes = new HashMap<>();
		if (cur == next) return;
		c.modes.put(cur.name(), read(c));
		Map<String, Boolean> load = c.modes.computeIfAbsent(next.name(), k -> preset(next));
		write(c, load, next);
		c.mode = next.name();
		c.save();
		if (announce && Minecraft.getInstance().player != null) {
			SchemShare.msg(next.label + " mode", ChatFormatting.LIGHT_PURPLE);
		}
	}

	/** Put a mode back to its built-in preset. */
	public static void reset(Mode m) {
		Config c = FactionsClient.CONFIG;
		c.modes.put(m.name(), preset(m));
		if (current() == m) write(c, c.modes.get(m.name()), m);
		c.save();
	}

	/** Server told us the realm. */
	public static void onRealm(String realm) {
		if (FactionsClient.CONFIG.autoMode) set(Mode.fromRealm(realm), true);
	}

	/** Joined a server that isn't RelapsedMC (or singleplayer). */
	public static void onOtherServer() {
		if (FactionsClient.CONFIG.autoMode) set(Mode.GENERAL, false);
	}

	/** First launch with modes: the player's current toggles become their General/Factions setup. */
	static void migrate(Config c) {
		if (c.modes == null) c.modes = new HashMap<>();
		if (c.modes.isEmpty()) {
			if (c.mode == null || c.mode.isBlank()) c.mode = Mode.FACTIONS.name();
			c.modes.put(c.mode, read(c));
		}
	}

	private static Map<String, Boolean> read(Config c) {
		Map<String, Boolean> m = new LinkedHashMap<>();
		for (String f : MANAGED) {
			try {
				m.put(f, field(f).getBoolean(c));
			} catch (Exception ignored) { }
		}
		return m;
	}

	private static void write(Config c, Map<String, Boolean> m, Mode mode) {
		Map<String, Boolean> base = preset(mode);
		for (String f : MANAGED) {
			Boolean v = m.get(f);
			if (v == null) v = base.getOrDefault(f, false); // field added in a later version
			try {
				field(f).setBoolean(c, v);
			} catch (Exception ignored) { }
		}
	}

	private static Field field(String name) throws NoSuchFieldException {
		return Config.class.getField(name);
	}
}
