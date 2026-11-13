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

	// Modules
	public boolean hud = true;
	public boolean crumbs = true;
	public boolean team = true;
	public boolean shareCrumbs = true;

	// HUD pieces
	public boolean hudCoords = true;
	public boolean hudArmor = true;
	public boolean hudEffects = true;
	public boolean hudTeamList = true;

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
					c.save(); // writes any new fields with defaults
					return c;
				}
			}
		} catch (Exception e) {
			FactionsClient.LOG.error("Failed to read config, using defaults", e);
		}
		Config c = new Config();
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
