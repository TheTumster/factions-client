package com.factionsclient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Settings saved to .minecraft/config/factionsclient.json */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("factionsclient.json");

	// Backend (Supabase). Same values for everyone in the group.
	public String supabaseUrl = "";
	public String supabaseAnonKey = "";
	/** Shared secret for your group. Anyone without it sees nothing. */
	public String teamKey = "";

	// Modes (one preset per realm: see Modes.java)
	public String mode = "";
	/** Switch mode automatically when RelapsedMC says which realm you joined. */
	public boolean autoMode = true;
	/** mode name -> that mode's module toggles */
	public java.util.Map<String, java.util.Map<String, Boolean>> modes = new java.util.HashMap<>();

	// Modules
	public boolean hud = true;
	public boolean crumbs = true;
	public boolean team = true;
	public boolean shareCrumbs = true;
	/** Faction claim borders in the world, colored by relation (RelapsedMC sends them). */
	public boolean claimMap = true;
	public int claimWallHeight = 6;
	/** Beam at your faction's /f home. */
	public boolean homeBeacon = true;
	/** Keep play.relapsedmc.com pinned to the top of the server list. */
	public boolean pinServer = true;
	public boolean chunkGrid = false;
	public int chunkHeight = 24;
	public boolean chunkCorners = true;
	public boolean fullbright = false;
	/** Always sprint when walking forward (separate from vanilla tap-to-toggle sprint). */
	public boolean autoSprint = false;
	public int zoomFov = 15;

	// HUD pieces
	public boolean hudCoords = true;
	public boolean hudArmor = true;
	public boolean hudEffects = true;
	public boolean hudTeamList = true;
	public boolean hudKeystrokes = true;
	public boolean hudStatus = true;
	public boolean hudInfo = true;
	public boolean hudTerritory = true;
	/** 0 glass panels, 1 minimal, 2 classic black boxes */
	public int hudStyle = 0;
	/** Glass panel opacity, percent */
	public int hudOpacity = 66;

	// Look
	public boolean customMenu = true;
	public boolean restyleButtons = true;

	// Discord
	public boolean discordPresence = true;
	public boolean discordShowServer = true;
	/** Leave blank to use the built-in laG Client app. */
	public String discordAppId = "";

	/** HUD element positions: id -> {x fraction, y fraction, scale} */
	public java.util.Map<String, float[]> hudPos = new java.util.HashMap<>();

	// Colors (ARGB)
	public int accentColor = 0xFF38E0FF;
	public int tntColor = 0xFFFF5A1F;
	public int sandColor = 0xFFF2D16B;
	public int chunkColor = 0xFFFFE040;

	/** One-time: move Xaero's world map off M so it doesn't clash with Litematica. */
	public boolean xaeroKeyFixed = false;

	// Crumbs
	public int crumbSeconds = 20;
	public int crumbRange = 160;

	public boolean backendReady() {
		return !supabaseUrl.isBlank() && !supabaseAnonKey.isBlank() && !teamKey.isBlank();
	}

	public static Config load() {
		try {
			if (Files.exists(FILE)) {
				Config c = GSON.fromJson(Files.readString(FILE), Config.class);
				if (c != null) {
					if (c.hudPos == null) c.hudPos = new java.util.HashMap<>();
					Modes.migrate(c);
					c.save(); // writes any new fields with defaults
					return c;
				}
			}
		} catch (Exception e) {
			FactionsClient.LOG.error("Failed to read config, using defaults", e);
		}
		Config c = new Config();
		c.mode = Modes.Mode.GENERAL.name();
		Modes.migrate(c);
		c.save();
		return c;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(this));
		} catch (IOException e) {
			FactionsClient.LOG.error("Failed to save config", e);
		}
	}
}
