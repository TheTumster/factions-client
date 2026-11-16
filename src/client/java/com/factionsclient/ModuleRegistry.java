package com.factionsclient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import net.fabricmc.loader.api.FabricLoader;

import com.factionsclient.ui.Theme;

/** Every feature in the client, its category, its on/off switch and all of its settings. */
public final class ModuleRegistry {
	public enum Category {
		FACTIONS("Factions", "⚔"), SCHEMATICA("Schematica", "▦"), RENDER("Render", "☀"), MOVEMENT("Movement", "➜"),
		HUD("HUD", "▣"), MODS("Mods", "✦"), CLIENT("Client", "⚙");

		public final String label;
		public final String icon;

		Category(String label, String icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	// ---- settings ----

	public abstract static class Setting {
		public final String name;

		Setting(String name) {
			this.name = name;
		}
	}

	public static final class BoolS extends Setting {
		public final Supplier<Boolean> get;
		public final Consumer<Boolean> set;

		public BoolS(String name, Supplier<Boolean> get, Consumer<Boolean> set) {
			super(name);
			this.get = get;
			this.set = set;
		}
	}

	public static final class IntS extends Setting {
		public final int min, max, step;
		public final String suffix;
		public final Supplier<Integer> get;
		public final Consumer<Integer> set;

		public IntS(String name, int min, int max, int step, String suffix, Supplier<Integer> get, Consumer<Integer> set) {
			super(name);
			this.min = min;
			this.max = max;
			this.step = step;
			this.suffix = suffix;
			this.get = get;
			this.set = set;
		}
	}

	public static final class ColorS extends Setting {
		public final Supplier<Integer> get;
		public final Consumer<Integer> set;

		public ColorS(String name, Supplier<Integer> get, Consumer<Integer> set) {
			super(name);
			this.get = get;
			this.set = set;
		}
	}

	public static final class KeyS extends Setting {
		public final Supplier<KeyMapping> key;

		public KeyS(String name, Supplier<KeyMapping> key) {
			super(name);
			this.key = key;
		}
	}

	public static final class ActionS extends Setting {
		public final String button;
		public final Runnable run;

		public ActionS(String name, String button, Runnable run) {
			super(name);
			this.button = button;
			this.run = run;
		}
	}

	public static final class TextS extends Setting {
		public final String hint;
		public final int maxLen;
		public String value = "";
		public final String button;
		public final Consumer<String> submit;

		public TextS(String name, String hint, int maxLen, String button, Consumer<String> submit) {
			super(name);
			this.hint = hint;
			this.maxLen = maxLen;
			this.button = button;
			this.submit = submit;
		}
	}

	public static final class InfoS extends Setting {
		public final Supplier<String> text;

		public InfoS(Supplier<String> text) {
			super("");
			this.text = text;
		}
	}

	// ---- modules ----

	public static final class Module {
		public final String name;
		public final String desc;
		public final Category category;
		public BoolS toggle;              // null = no on/off switch
		public Runnable open;             // non-null = clicking the card runs this instead of showing settings
		public Supplier<List<Setting>> settings = List::of;

		Module(String name, String desc, Category category) {
			this.name = name;
			this.desc = desc;
			this.category = category;
		}

		Module toggle(Supplier<Boolean> get, Consumer<Boolean> set) {
			this.toggle = new BoolS(name, get, set);
			return this;
		}

		Module settings(Supplier<List<Setting>> s) {
			this.settings = s;
			return this;
		}

		Module opens(Runnable r) {
			this.open = r;
			return this;
		}
	}

	public static final List<Module> ALL = new ArrayList<>();

	private ModuleRegistry() { }

	private static Config c() {
		return FactionsClient.CONFIG;
	}

	private static Module add(Module m) {
		ALL.add(m);
		return m;
	}

	/** Set by LaGMenu so actions that open other screens can return to it. */
	public static Screen parentScreen;

	public static void init() {
		if (!ALL.isEmpty()) return;

		// ------------------------------------------------ FACTIONS
		add(new Module("TNT Crumbs", "Trails for every TNT & falling sand, red where TNT blew", Category.FACTIONS)
				.toggle(() -> c().crumbs, v -> c().crumbs = v)
				.settings(() -> List.of(
						new IntS("Trail time", 5, 120, 5, "s", () -> c().crumbSeconds, v -> c().crumbSeconds = v),
						new IntS("Range", 32, 512, 16, " blocks", () -> c().crumbRange, v -> c().crumbRange = v),
						new ColorS("TNT trail", () -> c().tntColor, v -> c().tntColor = v),
						new ColorS("Sand trail", () -> c().sandColor, v -> c().sandColor = v),
						new ActionS("Clear all crumbs", "Clear", Crumbs::clear),
						new KeyS("Toggle key", () -> FactionsClient.crumbsKey),
						new KeyS("Clear key", () -> FactionsClient.clearKey))));

		add(new Module("Patch Crumbs", "TNT hitting your faction's claims shows up for you (purple boxes)", Category.FACTIONS)
				.toggle(() -> c().shareCrumbs, v -> c().shareCrumbs = v)
				.settings(() -> List.of(new InfoS(ModuleRegistry::backendLine))));

		add(new Module("Chunk Borders", "Claim-style chunk walls with a block grid", Category.FACTIONS)
				.toggle(() -> c().chunkGrid, v -> c().chunkGrid = v)
				.settings(() -> List.of(
						new ColorS("Color", () -> c().chunkColor, v -> c().chunkColor = v),
						new IntS("Height above/below you", 8, 96, 8, " blocks", () -> c().chunkHeight, v -> c().chunkHeight = v),
						new BoolS("Show neighbor corners", () -> c().chunkCorners, v -> c().chunkCorners = v),
						new KeyS("Toggle key", () -> FactionsClient.chunkKey))));

		add(new Module("Team Tracking", "See your faction members on the HUD and as beacons", Category.FACTIONS)
				.toggle(() -> c().team, v -> c().team = v)
				.settings(() -> List.of(new InfoS(ModuleRegistry::backendLine))));

		add(new Module("Claim Map", "Faction claims around you as colored walls (RelapsedMC)", Category.FACTIONS)
				.toggle(() -> c().claimMap, v -> c().claimMap = v)
				.settings(() -> List.of(
						new IntS("Wall height", 2, 24, 1, " blocks", () -> c().claimWallHeight, v -> c().claimWallHeight = v),
						new InfoS(() -> "Green you · Purple ally · Cyan truce · White neutral · Red enemy"),
						new InfoS(ModuleRegistry::backendLine))));

		add(new Module("Home Beacon", "Beam + distance to your faction's /f home (RelapsedMC)", Category.FACTIONS)
				.toggle(() -> c().homeBeacon, v -> c().homeBeacon = v)
				.settings(() -> List.of(new InfoS(() -> ServerLink.HOME == null ? "No /f home set (or not on RelapsedMC)."
						: "Home: " + (int) ServerLink.HOME.x() + " " + (int) ServerLink.HOME.y() + " " + (int) ServerLink.HOME.z()))));

		add(new Module("Schem Share", "Send schematics to your team with a code", Category.FACTIONS)
				.settings(ModuleRegistry::schemShareSettings));

		// ------------------------------------------------ SCHEMATICA
		add(new Module("Printer", "Auto-places the loaded schematic around you", Category.SCHEMATICA)
				.toggle(() -> Boolean.TRUE.equals(Litematica.printer()), Litematica::setPrinter)
				.settings(() -> List.of(
						new InfoS(() -> Litematica.printer() == null ? "Printer mod not found in this profile." : "Caps Lock also toggles it in-game."),
						new ActionS("Speed, range & more", "Open", () -> Litematica.open("GuiConfigs", parentScreen)))));

		add(new Module("Litematica", "Load, place and manage schematics", Category.SCHEMATICA)
				.settings(() -> {
					List<Setting> s = new ArrayList<>();
					if (!Litematica.present()) {
						s.add(new InfoS(() -> "Litematica isn't installed in this profile."));
						return s;
					}
					s.add(new ActionS("Main menu", "Open", () -> Litematica.open("GuiMainMenu", parentScreen)));
					s.add(new ActionS("Load a schematic", "Browse", () -> Litematica.open("GuiSchematicLoad", parentScreen)));
					s.add(new ActionS("Placements", "Open", () -> Litematica.open("GuiSchematicPlacementsList", parentScreen)));
					s.add(new ActionS("Loaded schematics", "Open", () -> Litematica.open("GuiSchematicLoadedList", parentScreen)));
					s.add(new ActionS("Litematica settings", "Open", () -> Litematica.open("GuiConfigs", parentScreen)));
					if (Litematica.schematicRendering() != null)
						s.add(new BoolS("Show schematic", () -> Boolean.TRUE.equals(Litematica.schematicRendering()), Litematica::setSchematicRendering));
					if (Litematica.easyPlaceMode() != null)
						s.add(new BoolS("Easy Place", () -> Boolean.TRUE.equals(Litematica.easyPlaceMode()), Litematica::setEasyPlaceMode));
					return s;
				}));

		// ------------------------------------------------ RENDER
		add(new Module("Fullbright", "See in caves and at night like it's daytime", Category.RENDER)
				.toggle(() -> c().fullbright, v -> c().fullbright = v)
				.settings(() -> List.of(new KeyS("Toggle key", () -> FactionsClient.fullbrightKey))));

		add(new Module("Zoom", "Hold to zoom in, like OptiFine", Category.RENDER)
				.settings(() -> List.of(
						new IntS("Zoom FOV (lower = closer)", 5, 50, 1, "°", () -> c().zoomFov, v -> c().zoomFov = v),
						new KeyS("Zoom key (hold)", () -> FactionsClient.zoomKey))));

		add(new Module("Shaders", "Iris shader packs", Category.RENDER).opens(() -> openModConfig("iris")));

		// ------------------------------------------------ MOVEMENT
		var opts = Minecraft.getInstance().options;
		add(new Module("Toggle Sprint", "Tap sprint once to keep sprinting", Category.MOVEMENT)
				.toggle(() -> opts.toggleSprint().get(), v -> {
					opts.toggleSprint().set(v);
					opts.save();
				}));
		add(new Module("Toggle Sneak", "Tap sneak once to keep sneaking", Category.MOVEMENT)
				.toggle(() -> opts.toggleCrouch().get(), v -> {
					opts.toggleCrouch().set(v);
					opts.save();
				}));
		add(new Module("Auto Sprint", "Always sprint while walking forward", Category.MOVEMENT)
				.toggle(() -> c().autoSprint, v -> c().autoSprint = v));

		// ------------------------------------------------ HUD
		add(new Module("Edit HUD Layout", "Drag and resize everything on your HUD", Category.HUD)
				.opens(() -> Minecraft.getInstance().setScreen(new HudEditorScreen(parentScreen))));
		add(new Module("HUD", "Master switch for every HUD element", Category.HUD)
				.toggle(() -> c().hud, v -> c().hud = v));
		hudElement("info", "FPS / Ping / TNT", "Performance and live TNT count", () -> c().hudInfo, v -> c().hudInfo = v);
		hudElement("coords", "Coordinates", "XYZ, facing and nether coords", () -> c().hudCoords, v -> c().hudCoords = v);
		hudElement("status", "Sprint / Sneak Status", "[Sprinting (Toggled)]", () -> c().hudStatus, v -> c().hudStatus = v);
		hudElement("keystrokes", "Keystrokes + CPS", "WASD, mouse buttons and clicks per second", () -> c().hudKeystrokes, v -> c().hudKeystrokes = v);
		hudElement("effects", "Potion Effects", "Active effects with timers in their colors", () -> c().hudEffects, v -> c().hudEffects = v);
		hudElement("armor", "Armor", "Armor pieces and durability", () -> c().hudArmor, v -> c().hudArmor = v);
		hudElement("territory", "Territory", "Whose land you're standing in, colored by relation", () -> c().hudTerritory, v -> c().hudTerritory = v);
		hudElement("team", "Team List", "Teammates, distance and direction", () -> c().hudTeamList, v -> c().hudTeamList = v);

		// ------------------------------------------------ MODS (their own settings screens)
		modCard("Sodium", "Video & performance settings", "sodium");
		modCard("Iris Shaders", "Shader packs", "iris");
		modCard("Xaero's Minimap", "Minimap, radar & waypoints", "xaerominimap");
		modCard("Xaero's World Map", "Full screen map", "xaeroworldmap");
		modCard("Litematica", "Schematic tool settings", "litematica");
		modCard("Entity Culling", "Hide what you can't see for FPS", "entityculling");
		add(new Module("All Mods", "Every installed mod and its settings", Category.MODS).opens(ModuleRegistry::openModList));

		// ------------------------------------------------ CLIENT
		add(new Module("Menu Key", "The key that opens this menu", Category.CLIENT)
				.settings(() -> List.of(new KeyS("Open menu", () -> FactionsClient.menuKey))));
		add(new Module("Custom Main Menu", "Custom title screen instead of Minecraft's", Category.CLIENT)
				.toggle(() -> c().customMenu, v -> c().customMenu = v));
		add(new Module("Styled Buttons", "Gradient style on every menu's buttons", Category.CLIENT)
				.toggle(() -> c().restyleButtons, v -> c().restyleButtons = v));
		add(new Module("Discord Presence", "Show \"Playing RelapsedMC\" on Discord", Category.CLIENT)
				.toggle(() -> c().discordPresence, v -> c().discordPresence = v)
				.settings(() -> List.of(new BoolS("Show server address", () -> c().discordShowServer, v -> c().discordShowServer = v),
						new InfoS(() -> "Turn off Discord RPC in the Modrinth App so it doesn't override this."))));
		add(new Module("Updates", "The client updates itself on launch", Category.CLIENT)
				.settings(() -> List.of(new InfoS(() -> Updater.pending != null ? Updater.pending : "You're on the latest build."))));
		add(new Module("Pin RelapsedMC", "Keep play.relapsedmc.com at the top of your server list", Category.CLIENT)
				.toggle(() -> c().pinServer, v -> c().pinServer = v));
		add(new Module("RelapsedMC Link", "Faction data sent by the RelapsedMC server", Category.CLIENT)
				.settings(() -> List.of(new InfoS(ModuleRegistry::backendLine),
						new ActionS("Reload config file", "Reload", () -> FactionsClient.CONFIG = Config.load()))));
	}

	private static void hudElement(String id, String name, String desc, Supplier<Boolean> get, Consumer<Boolean> set) {
		add(new Module(name, desc, Category.HUD)
				.toggle(get, set)
				.settings(() -> List.of(
						new IntS("Size", 50, 300, 10, "%", () -> Math.round(Hud.pos(id)[2] * 100), v -> Hud.pos(id)[2] = v / 100f),
						new ActionS("Position", "Reset", () -> c().hudPos.put(id, Hud.defaultPos(id))),
						new ActionS("Move it", "Edit layout", () -> Minecraft.getInstance().setScreen(new HudEditorScreen(parentScreen))))));
	}

	private static void modCard(String name, String desc, String modId) {
		if (!FabricLoader.getInstance().isModLoaded(modId)) return;
		add(new Module(name, desc, Category.MODS).opens(() -> openModConfig(modId)));
	}

	private static List<Setting> schemShareSettings() {
		List<Setting> s = new ArrayList<>();
		s.add(new TextS("Download a code", "ABC123", 6, "Download", code -> {
			if (!code.isBlank()) SchemShare.download(code);
		}));
		s.add(new ActionS("Team's recent shares", "Show in chat", SchemShare::listToChat));
		List<String> files = SchemShare.localFiles();
		if (files.isEmpty()) s.add(new InfoS(() -> "No schematics in your schematics folder yet."));
		for (String f : files.subList(0, Math.min(12, files.size()))) {
			s.add(new ActionS(f, "Upload", () -> SchemShare.uploadFile(f)));
		}
		return s;
	}

	static String backendLine() {
		return ServerLink.status();
	}

	// ---- third-party config screens through Mod Menu ----

	public static void openModConfig(String modId) {
		Minecraft mc = Minecraft.getInstance();
		try {
			Class<?> mm = Class.forName("com.terraformersmc.modmenu.ModMenu");
			Object screen = mm.getMethod("getConfigScreen", String.class, Screen.class).invoke(null, modId, parentScreen);
			if (screen instanceof Screen s) {
				mc.setScreen(s);
				return;
			}
		} catch (Throwable t) {
			FactionsClient.LOG.warn("Mod Menu config lookup failed for {}: {}", modId, t.toString());
		}
		if (modId.equals("litematica")) {
			Litematica.open("GuiConfigs", parentScreen);
			return;
		}
		SchemShare.msg("That mod has no settings screen (or Mod Menu is missing).", ChatFormatting.GRAY);
	}

	public static void openModList() {
		try {
			Class<?> c = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			Minecraft.getInstance().setScreen((Screen) c.getConstructor(Screen.class).newInstance(parentScreen));
		} catch (Throwable t) {
			SchemShare.msg("Mod Menu isn't installed.", ChatFormatting.GRAY);
		}
	}

	public static String brand() {
		return Theme.BRAND;
	}
}
