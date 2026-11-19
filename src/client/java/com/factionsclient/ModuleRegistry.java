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
		MODES("Modes", "◈"), TEAM("Team", "♦"), FACTIONS("Factions", "⚔"), SCHEMATICA("Schematica", "▦"), RENDER("Render", "☀"), MOVEMENT("Movement", "➜"),
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

		/** Optional: how to show the value (e.g. 0.1 steps, "Unlimited"). */
		public java.util.function.IntFunction<String> fmt;

		public IntS(String name, int min, int max, int step, String suffix, Supplier<Integer> get, Consumer<Integer> set) {
			super(name);
			this.min = min;
			this.max = max;
			this.step = step;
			this.suffix = suffix;
			this.get = get;
			this.set = set;
		}

		public IntS fmt(java.util.function.IntFunction<String> f) {
			this.fmt = f;
			return this;
		}

		public String show(int v) {
			return fmt != null ? fmt.apply(v) : v + suffix;
		}
	}

	/** A value you click through (Off / Fast / Fancy...). Left click = next, right click = previous. */
	public static final class ChoiceS extends Setting {
		public final Supplier<String> value;
		public final Consumer<Boolean> cycle; // true = forward

		public ChoiceS(String name, Supplier<String> value, Consumer<Boolean> cycle) {
			super(name);
			this.value = value;
			this.cycle = cycle;
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
		/** For keys that aren't vanilla KeyMappings (Litematica/Printer hotkeys): current label + rebind (-1 = none). */
		public final Supplier<String> label;
		public final java.util.function.IntConsumer bind;

		public KeyS(String name, Supplier<KeyMapping> key) {
			super(name);
			this.key = key;
			this.label = null;
			this.bind = null;
		}

		public KeyS(String name, Supplier<String> label, java.util.function.IntConsumer bind) {
			super(name);
			this.key = () -> null;
			this.label = label;
			this.bind = bind;
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

		private Boolean hasSettingsCache;

		/** Cached: building some pages (other mods' options) is too heavy to do every frame. */
		public boolean hasSettings() {
			if (hasSettingsCache == null) hasSettingsCache = !settings.get().isEmpty();
			return hasSettingsCache;
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
		// ------------------------------------------------ MODES (one preset per realm)
		for (Modes.Mode m : Modes.Mode.values()) {
			add(new Module(m.icon + "  " + m.label, m.desc, Category.MODES)
					.toggle(() -> Modes.current() == m, v -> {
						if (v) Modes.set(m, false);
					})
					.settings(() -> List.of(
							new InfoS(() -> Modes.current() == m ? "Active. Changes you make now are saved to " + m.label + "."
									: "Click the switch to use " + m.label + "."),
							new ActionS("Back to the default " + m.label + " setup", "Reset", () -> Modes.reset(m)))));
		}
		add(new Module("Auto-switch by realm", "RelapsedMC tells the client which realm you joined", Category.MODES)
				.toggle(() -> c().autoMode, v -> c().autoMode = v)
				.settings(() -> List.of(new InfoS(() -> ServerLink.REALM.isEmpty() ? "Not on RelapsedMC right now."
						: "You're on " + ServerLink.REALM + "."))));

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

		add(new Module("Shot Tracer", "Traces cannon shots back to the cannon that fired them", Category.FACTIONS)
				.toggle(() -> c().shotTracer, v -> c().shotTracer = v)
				.settings(() -> List.of(
						new IntS("Keep lines for", 5, 300, 5, "s", () -> c().shotSeconds, v -> c().shotSeconds = v),
						new IntS("Min shot speed (x0.1 blocks/tick)", 3, 20, 1, "", () -> c().shotMinSpeed, v -> c().shotMinSpeed = v),
						new ColorS("Trace line", () -> c().shotColor, v -> c().shotColor = v),
						new ColorS("Cannon marker", () -> c().cannonColor, v -> c().cannonColor = v),
						new ActionS("Clear traces", "Clear", ShotTracer::clear))));

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

		// ------------------------------------------------ TEAM (RelapsedMC Teams: works on every realm)
		add(new Module("Your Team", "Create a team, invite friends, see who's online and where", Category.TEAM)
				.settings(ModuleRegistry::teamPage));
		add(new Module("Team Schematics", "Share schematics with your team: one click to download", Category.TEAM)
				.settings(ModuleRegistry::teamSchemPage));
		add(new Module("Team Tracking", "Teammates as pink beacons + in the HUD team list", Category.TEAM)
				.toggle(() -> c().teamTrack, v -> c().teamTrack = v));
		add(new Module("Team Pings", "Press Z to ping where you look (Shift+Z = enemy)", Category.TEAM)
				.toggle(() -> c().teamPings, v -> c().teamPings = v)
				.settings(() -> List.of(new KeyS("Ping key", () -> FactionsClient.pingKey),
						new InfoS(() -> "Teammates on the same realm see a marker + hear a chime for 12s."))));
		add(new Module("Schem Share (old)", "Private-group codes: /ss upload, /ss get (team pack only)", Category.FACTIONS)
				.settings(ModuleRegistry::schemShareSettings));

		// ------------------------------------------------ SCHEMATICA
		add(new Module("Printer", "Auto-places the loaded schematic around you", Category.SCHEMATICA)
				.toggle(() -> Boolean.TRUE.equals(Litematica.printer()), Litematica::setPrinter)
				.settings(() -> {
					List<Setting> s = new ArrayList<>();
					if (Litematica.printer() == null) {
						s.add(new InfoS(() -> "Printer mod not found in this profile."));
						return s;
					}
					// Only the printer's own options (its config lists also contain everything Litematica has)
					s.addAll(ModBridges.malilib(ModBridges.malilibFields("me.aleksilassila.litematica.printer.config.Configs", "PRINT_MODE") /* = the Enabled switch */, ModBridges::saveLitematica));
					s.addAll(ModBridges.malilib(ModBridges.malilibFields("me.aleksilassila.litematica.printer.config.Hotkeys"), ModBridges::saveLitematica));
					return s;
				}));

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
					if (Litematica.schematicRendering() != null)
						s.add(new BoolS("Show schematic", () -> Boolean.TRUE.equals(Litematica.schematicRendering()), Litematica::setSchematicRendering));
					if (Litematica.easyPlaceMode() != null)
						s.add(new BoolS("Easy Place", () -> Boolean.TRUE.equals(Litematica.easyPlaceMode()), Litematica::setEasyPlaceMode));
					return s;
				}));

		// Every Litematica setting, in our UI (same options as its own config screen)
		litematicaPage("Litematica: General", "Placement, Easy Place, pasting, pick block", "Configs$Generic", "OPTIONS");
		litematicaPage("Litematica: Visuals", "How schematics and overlays are drawn", "Configs$Visuals", "OPTIONS");
		litematicaPage("Litematica: Info Overlays", "Block info, verifier and HUD text", "Configs$InfoOverlays", "OPTIONS");
		litematicaPage("Litematica: Colors", "Overlay and highlight colors", "Configs$Colors", "OPTIONS");
		litematicaPage("Litematica: Hotkeys", "Every Litematica key binding", "Hotkeys", "HOTKEY_LIST");

		// ------------------------------------------------ RENDER
		add(new Module("Video & Performance", "Render distance, FPS cap, clouds, graphics, FOV", Category.RENDER)
				.settings(Performance::settings));

		add(new Module("Minimap", "Xaero's minimap: size, radar, waypoints, cave mode, everything", Category.RENDER)
				.toggle(() -> Boolean.TRUE.equals(ModBridges.xaeroBool("DISPLAY_MINIMAP")), v -> ModBridges.setXaeroBool("DISPLAY_MINIMAP", v))
				.settings(ModBridges::xaeroMinimap));

		add(new Module("Fullbright", "See in caves and at night like it's daytime", Category.RENDER)
				.toggle(() -> c().fullbright, v -> c().fullbright = v)
				.settings(() -> List.of(new KeyS("Toggle key", () -> FactionsClient.fullbrightKey))));

		add(new Module("Motion Blur", "Smooth trails when you turn, like OptiFine-era clients", Category.RENDER)
				.toggle(() -> c().motionBlur, v -> c().motionBlur = v)
				.settings(() -> List.of(
						new IntS("Strength", 1, 10, 1, "", () -> c().motionBlurStrength, v -> c().motionBlurStrength = v))));

		add(new Module("Zoom", "Hold to zoom in, like OptiFine", Category.RENDER)
				.settings(() -> List.of(
						new IntS("Zoom FOV (lower = closer)", 5, 50, 1, "°", () -> c().zoomFov, v -> c().zoomFov = v),
						new BoolS("Slower mouse while zoomed", () -> c().zoomScaleSensitivity, v -> c().zoomScaleSensitivity = v),
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
		add(new Module("HUD Style", "Glass panels, minimal text, or classic boxes", Category.HUD)
				.settings(() -> List.of(
						new IntS("Style (0 glass, 1 minimal, 2 classic)", 0, 2, 1, "", () -> c().hudStyle, v -> c().hudStyle = v),
						new IntS("Glass opacity", 20, 100, 5, "%", () -> c().hudOpacity, v -> c().hudOpacity = v))));
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
		add(new Module("Chat Tabs", "TabbyChat-style tabs: Faction, Ally, Private, Server + your own", Category.CLIENT)
				.toggle(() -> c().chatTabs, v -> {
					c().chatTabs = v;
					ChatTabs.reset();
					net.minecraft.client.Minecraft.getInstance().gui.getChat().rescaleChat();
				})
				.settings(() -> {
					List<Setting> s = new ArrayList<>();
					s.add(new BoolS("Team tab", () -> c().chatTabTeam, v -> { c().chatTabTeam = v; ChatTabs.rebuild(); }));
					s.add(new BoolS("Faction tab", () -> c().chatTabFaction, v -> { c().chatTabFaction = v; ChatTabs.rebuild(); }));
					s.add(new BoolS("Ally tab", () -> c().chatTabAlly, v -> { c().chatTabAlly = v; ChatTabs.rebuild(); }));
					s.add(new BoolS("Private tab", () -> c().chatTabPrivate, v -> { c().chatTabPrivate = v; ChatTabs.rebuild(); }));
					s.add(new BoolS("Server tab (announcements, no player chat)", () -> c().chatTabServer, v -> { c().chatTabServer = v; ChatTabs.rebuild(); }));
					s.add(new BoolS("Typing in a tab sends there", () -> c().chatRoute, v -> c().chatRoute = v));
					s.add(new BoolS("Timestamps", () -> c().chatTimestamps, v -> c().chatTimestamps = v));
					s.add(new BoolS("Stack repeated messages (x3)", () -> c().chatStack, v -> c().chatStack = v));
					s.add(new BoolS("Sound on private message", () -> c().chatPrivatePing, v -> c().chatPrivatePing = v));
					// A color for every tab
					List<String> names = new ArrayList<>(List.of(ChatTabs.BUILTIN));
					for (String spec : c().chatCustomTabs) if (spec.indexOf(':') > 0) names.add(spec.substring(0, spec.indexOf(':')).trim());
					for (String n : names) {
						s.add(new ColorS(n + " tab color", () -> c().chatTabColors.getOrDefault(n, ChatTabs.defaultColor(n) & 0xFFFFFF),
								v -> {
									c().chatTabColors.put(n, v & 0xFFFFFF);
									ChatTabs.rebuild();
								}));
					}
					for (String spec : new ArrayList<>(c().chatCustomTabs)) {
						s.add(new ActionS("Tab  " + spec, "Remove", () -> {
							c().chatCustomTabs.remove(spec);
							ChatTabs.rebuild();
						}));
					}
					s.add(new TextS("Add a tab  (Name: words|or|regex)", "Trades: wts|wtb|selling", 120, "Add", v -> {
						if (v.indexOf(':') > 0) {
							c().chatCustomTabs.add(v.trim());
							ChatTabs.rebuild();
						}
					}));
					return s;
				}));

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

	private static List<Setting> teamPage() {
		List<Setting> s = new ArrayList<>();
		if (!TeamLink.connected) {
			s.add(new InfoS(() -> "Teams work on RelapsedMC. Join play.relapsedmc.com."));
			return s;
		}
		if (!TeamLink.inTeam()) {
			s.add(new InfoS(() -> "You're not in a team yet. Teams work on every realm."));
			s.add(new TextS("Create a team", "3-16 letters, numbers or _", 16, "Create", v -> TeamLink.cmd("team create " + v.trim())));
			for (TeamLink.Invite inv : TeamLink.invites) {
				s.add(new ActionS("Invite: " + inv.team() + "  (from " + inv.by() + ")", "Accept", () -> TeamLink.cmd("team accept " + inv.team())));
				s.add(new ActionS("    decline " + inv.team(), "Deny", () -> TeamLink.cmd("team deny " + inv.team())));
			}
			return s;
		}
		long online = TeamLink.members.stream().filter(TeamLink.Member::online).count();
		s.add(new InfoS(() -> TeamLink.team + "  ·  " + online + "/" + TeamLink.members.size() + " online  ·  leader " + TeamLink.leader));
		for (TeamLink.Member m : TeamLink.members) {
			String line = (m.online() ? "§a● §f" : "§8● §7") + m.name() + (m.online() && !m.realm().isEmpty() ? "  §7on " + m.realm() : "")
					+ (m.name().equals(TeamLink.leader) ? "  §d★" : "");
			boolean self = net.minecraft.client.Minecraft.getInstance().player != null
					&& m.name().equals(net.minecraft.client.Minecraft.getInstance().getUser().getName());
			if (TeamLink.youLead && !self) {
				s.add(new ActionS(line, "Remove", () -> TeamLink.cmd("team kick " + m.name())));
			} else {
				s.add(new InfoS(() -> line));
			}
		}
		if (TeamLink.youLead) s.add(new TextS("Invite a player", "Their Minecraft name", 16, "Invite", v -> TeamLink.cmd("team invite " + v.trim())));
		s.add(new ActionS("Leave " + TeamLink.team, "Leave", () -> TeamLink.cmd("team leave")));
		if (TeamLink.youLead) s.add(new ActionS("Disband the team (everyone is removed)", "Disband", () -> TeamLink.cmd("team disband")));
		s.add(new InfoS(() -> "Team chat: the Team tab in chat, or /tc <message>"));
		return s;
	}

	private static String pickedSchem = null;

	private static List<Setting> teamSchemPage() {
		List<Setting> s = new ArrayList<>();
		if (!TeamLink.connected || !TeamLink.inTeam()) {
			s.add(new InfoS(() -> TeamLink.connected ? "Join or create a team first (Your Team)." : "Team schematics work on RelapsedMC."));
			return s;
		}
		List<String> files = TeamLink.localFiles();
		if (files.isEmpty()) {
			s.add(new InfoS(() -> "No schematics in your schematics folder yet (save one with Litematica)."));
		} else {
			if (pickedSchem == null || !files.contains(pickedSchem)) pickedSchem = files.get(0);
			s.add(new ChoiceS("File", () -> pickedSchem, fwd -> {
				int i = files.indexOf(pickedSchem), n = files.size();
				pickedSchem = files.get(((i < 0 ? 0 : i) + (fwd ? 1 : n - 1)) % n);
			}));
			s.add(new ActionS("Share it with " + TeamLink.team, "Upload", () -> TeamLink.upload(pickedSchem)));
		}
		s.add(new InfoS(() -> TeamLink.status.isEmpty() ? "Shared with your team:" : TeamLink.status));
		if (TeamLink.schems.isEmpty()) s.add(new InfoS(() -> "Nothing shared yet."));
		for (TeamLink.Schem sc : TeamLink.schems) {
			String label = sc.name() + "  §7" + sc.by() + " · " + (sc.size() / 1024) + " KB";
			s.add(new ActionS(label, "Download", () -> TeamLink.download(sc)));
			boolean mine = net.minecraft.client.Minecraft.getInstance().player != null
					&& sc.by().equals(net.minecraft.client.Minecraft.getInstance().getUser().getName());
			if (mine || TeamLink.youLead) s.add(new ActionS("    remove " + sc.name(), "Delete", () -> TeamLink.delete(sc)));
		}
		return s;
	}

	private static void litematicaPage(String name, String desc, String cls, String member) {
		if (!Litematica.present()) return;
		add(new Module(name, desc, Category.SCHEMATICA).settings(() ->
				ModBridges.malilib(ModBridges.malilibList("fi.dy.masa.litematica.config." + cls, member), ModBridges::saveLitematica)));
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
