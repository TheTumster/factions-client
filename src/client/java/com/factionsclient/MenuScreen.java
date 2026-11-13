package com.factionsclient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Right Shift control hub: every module and tool in one place, split into tabs. */
public class MenuScreen extends Screen {
	private enum Tab {
		FACTIONS("Factions"), SCHEMATICA("Schematica"), SHARE("Schem Share"), RENDER("Render & Player"), HUD("HUD"), MAP("Minimap");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static final int ACCENT = 0xFF38E0FF;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int MUTED = 0xFF9A9A9A;
	private static final int PANEL = 0xE0101418;
	private static final int SIDEBAR = 0xF00A0D10;

	private static Tab tab = Tab.FACTIONS;
	private static int filePage = 0;

	private final List<String[]> notes = new ArrayList<>(); // {text, x, y}
	private int px, py, pw, ph, cx, cy, colW;
	private EditBox codeBox;

	public MenuScreen() {
		super(Component.literal("Factions Client"));
	}

	@Override
	protected void init() {
		notes.clear();
		pw = Math.min(440, this.width - 20);
		ph = Math.min(270, this.height - 20);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;

		// Sidebar
		int sy = py + 28;
		for (Tab t : Tab.values()) {
			String label = (t == tab ? "§b▶ " : "  ") + t.label;
			addRenderableWidget(Button.builder(Component.literal(label), b -> {
				tab = t;
				rebuildWidgets();
			}).bounds(px + 6, sy, 100, 20).build());
			sy += 23;
		}
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
				.bounds(px + 6, py + ph - 26, 100, 20).build());

		cx = px + 118;
		cy = py + 28;
		colW = (pw - 118 - 16) / 2;

		Config c = FactionsClient.CONFIG;
		switch (tab) {
			case FACTIONS -> {
				toggle(0, 0, "TNT/Sand Crumbs", () -> c.crumbs, v -> c.crumbs = v);
				toggle(1, 0, "Share Patch Crumbs", () -> c.shareCrumbs, v -> c.shareCrumbs = v);
				toggle(0, 1, "Team Tracking", () -> c.team, v -> c.team = v);
				toggle(1, 1, "Chunk Borders", () -> c.chunkGrid, v -> c.chunkGrid = v);
				button(0, 2, "Crumb time: " + c.crumbSeconds + "s", b -> {
					c.crumbSeconds = c.crumbSeconds >= 60 ? 5 : c.crumbSeconds + 5;
					b.setMessage(Component.literal("Crumb time: " + c.crumbSeconds + "s"));
					c.save();
				});
				button(1, 2, "Crumb range: " + c.crumbRange, b -> {
					c.crumbRange = c.crumbRange >= 320 ? 64 : c.crumbRange + 32;
					b.setMessage(Component.literal("Crumb range: " + c.crumbRange));
					c.save();
				});
				button(0, 3, "Clear Crumbs (K)", b -> Crumbs.clear());
				note(0, 5, "Orange = TNT path, yellow = sand. Red box = TNT went off.");
				note(0, 6, "Purple box = a teammate saw TNT go off there.");
				note(0, 7, backendLine());
			}
			case SCHEMATICA -> {
				if (!Litematica.present()) {
					note(0, 0, "Litematica isn't installed in this profile.");
					break;
				}
				button(0, 0, "Litematica Menu", b -> Litematica.open("GuiMainMenu", this));
				button(1, 0, "Load Schematic", b -> Litematica.open("GuiSchematicLoad", this));
				button(0, 1, "Placements", b -> Litematica.open("GuiSchematicPlacementsList", this));
				button(1, 1, "Loaded Schematics", b -> Litematica.open("GuiSchematicLoadedList", this));
				button(0, 2, "Litematica Settings", b -> Litematica.open("GuiConfigs", this));

				Boolean printer = Litematica.printer();
				if (printer != null) {
					toggle(1, 2, "Printer", () -> Boolean.TRUE.equals(Litematica.printer()), Litematica::setPrinter);
				} else {
					note(1, 2, "Printer not found");
				}
				if (Litematica.schematicRendering() != null) {
					toggle(0, 3, "Show Schematic", () -> Boolean.TRUE.equals(Litematica.schematicRendering()), Litematica::setSchematicRendering);
				}
				if (Litematica.easyPlaceMode() != null) {
					toggle(1, 3, "Easy Place", () -> Boolean.TRUE.equals(Litematica.easyPlaceMode()), Litematica::setEasyPlaceMode);
				}
				note(0, 5, "Printer speed/range: Litematica Settings > Generic (bottom).");
				note(0, 6, "Caps Lock toggles the printer in-game.");
			}
			case SHARE -> {
				codeBox = new EditBox(this.font, cx, cy, colW, 20, Component.literal("Code"));
				codeBox.setMaxLength(6);
				codeBox.setHint(Component.literal("Paste code..."));
				addRenderableWidget(codeBox);
				button(1, 0, "Download code", b -> {
					if (!codeBox.getValue().isBlank()) {
						SchemShare.download(codeBox.getValue());
						onClose();
					}
				});
				button(0, 1, "Show team's shares in chat", b -> {
					SchemShare.listToChat();
					onClose();
				});

				List<String> files = SchemShare.localFiles();
				int perPage = 4;
				int pages = Math.max(1, (files.size() + perPage - 1) / perPage);
				filePage = Math.min(filePage, pages - 1);
				note(0, 3, "Upload one of your schematics (" + files.size() + " found):");
				for (int i = 0; i < perPage; i++) {
					int idx = filePage * perPage + i;
					if (idx >= files.size()) break;
					String f = files.get(idx);
					String label = f.length() > 44 ? "…" + f.substring(f.length() - 43) : f;
					addRenderableWidget(Button.builder(Component.literal("⬆ " + label), b -> {
						SchemShare.uploadFile(f);
						onClose();
					}).bounds(cx, rowY(4 + i) - 6, colW * 2 + 6, 20).build());
				}
				if (pages > 1) {
					addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
						filePage = (filePage - 1 + pages) % pages;
						rebuildWidgets();
					}).bounds(cx, rowY(8) - 6, 60, 20).build());
					addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
						filePage = (filePage + 1) % pages;
						rebuildWidgets();
					}).bounds(cx + 66, rowY(8) - 6, 60, 20).build());
				}
			}
			case RENDER -> {
				toggle(0, 0, "Fullbright", () -> c.fullbright, v -> c.fullbright = v);
				toggle(1, 0, "Toggle Sprint", () -> c.toggleSprint, v -> c.toggleSprint = v);
				button(0, 1, "Zoom FOV: " + c.zoomFov, b -> {
					c.zoomFov = c.zoomFov <= 5 ? 30 : c.zoomFov - 5;
					b.setMessage(Component.literal("Zoom FOV: " + c.zoomFov));
					c.save();
				});
				note(0, 3, "Hold C to zoom. Rebind in Controls > Factions Client.");
				note(0, 4, "Performance: Sodium + Entity Culling + FerriteCore.");
				note(0, 5, "Shaders: Video Settings > Shader Packs (Iris).");
			}
			case HUD -> {
				toggle(0, 0, "HUD (master)", () -> c.hud, v -> c.hud = v);
				toggle(1, 0, "Coords", () -> c.hudCoords, v -> c.hudCoords = v);
				toggle(0, 1, "Armor", () -> c.hudArmor, v -> c.hudArmor = v);
				toggle(1, 1, "Potion Effects", () -> c.hudEffects, v -> c.hudEffects = v);
				toggle(0, 2, "Team List", () -> c.hudTeamList, v -> c.hudTeamList = v);
				toggle(1, 2, "Keystrokes + CPS", () -> c.hudKeystrokes, v -> c.hudKeystrokes = v);
			}
			case MAP -> {
				note(0, 0, "Minimap: Xaero's Minimap (top-right of your screen).");
				note(0, 1, "Y = minimap settings   B = new waypoint");
				note(0, 2, "N = full world map (moved off M so Litematica keeps M)");
				note(0, 3, "[ and ] = zoom the minimap");
				note(0, 5, "Waypoints show on both the minimap and world map.");
				note(0, 6, "All keys can be rebound in Options > Controls.");
			}
		}
	}

	// ---- layout helpers ----

	private int rowY(int row) {
		return cy + row * 24 + 6;
	}

	private int colX(int col) {
		return cx + col * (colW + 6);
	}

	private void button(int col, int row, String label, Button.OnPress press) {
		addRenderableWidget(Button.builder(Component.literal(label), press)
				.bounds(colX(col), rowY(row) - 6, colW, 20).build());
	}

	private void toggle(int col, int row, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
		addRenderableWidget(Button.builder(onOff(label, get.get()), b -> {
			set.accept(!get.get());
			b.setMessage(onOff(label, get.get()));
			FactionsClient.CONFIG.save();
		}).bounds(colX(col), rowY(row) - 6, colW, 20).build());
	}

	private void note(int col, int row, String text) {
		notes.add(new String[] {text, String.valueOf(colX(col)), String.valueOf(rowY(row))});
	}

	private static Component onOff(String label, boolean on) {
		return Component.literal(label + ": " + (on ? "§aON" : "§cOFF"));
	}

	private static String backendLine() {
		Config c = FactionsClient.CONFIG;
		if (!c.backendReady()) return "Team backend: not set up (team + sharing disabled).";
		return TeamSync.lastError == null ? "Team backend: connected." : "Team backend error: " + TeamSync.lastError;
	}

	// ---- drawing ----

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.renderBackground(g, mouseX, mouseY, delta);
		g.fill(px, py, px + pw, py + ph, PANEL);
		g.fill(px, py, px + 112, py + ph, SIDEBAR);
		g.fill(px, py, px + pw, py + 1, ACCENT);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawString(this.font, "FACTIONS CLIENT", px + 8, py + 9, ACCENT, true);
		g.drawString(this.font, tab.label, cx, py + 9, TEXT, true);
		for (String[] n : notes) {
			g.drawString(this.font, n[0], Integer.parseInt(n[1]), Integer.parseInt(n[2]), MUTED, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
