package com.factionsclient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * HUD made of draggable elements. Each element draws itself at (0,0) in its own space and returns its size;
 * we translate/scale it to the position saved in the config. The HUD editor uses BOUNDS to drag them.
 */
public final class Hud {
	static final int WHITE = 0xFFFFFFFF;
	static final int GRAY = 0xFFAAAAAA;
	static final int RED = 0xFFFF5555;
	static final int BG = 0x90000000;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Element ids, in draw order, with their display names. */
	public static final Map<String, String> ELEMENTS = new LinkedHashMap<>();

	static {
		ELEMENTS.put("info", "FPS / Ping / TNT");
		ELEMENTS.put("coords", "Coords");
		ELEMENTS.put("territory", "Territory");
		ELEMENTS.put("status", "Sprint / Sneak");
		ELEMENTS.put("team", "Team List");
		ELEMENTS.put("keystrokes", "Keystrokes + CPS");
		ELEMENTS.put("effects", "Potion Effects");
		ELEMENTS.put("armor", "Armor");
	}

	/** Default position (fraction of screen) and scale for each element. */
	public static float[] defaultPos(String id) {
		return switch (id) {
			case "info" -> new float[] {0.005f, 0.01f, 1f};
			case "coords" -> new float[] {0.005f, 0.055f, 1f};
			case "territory" -> new float[] {0.46f, 0.02f, 1f};
			case "status" -> new float[] {0.005f, 0.135f, 1f};
			case "team" -> new float[] {0.005f, 0.18f, 1f};
			case "keystrokes" -> new float[] {0.93f, 0.30f, 1f}; // right side: the chat lives bottom-left
			case "effects" -> new float[] {0.85f, 0.01f, 1f};
			case "armor" -> new float[] {0.90f, 0.70f, 1f};
			default -> new float[] {0.1f, 0.1f, 1f};
		};
	}

	private static final Map<String, int[]> LAST_SIZE = new java.util.HashMap<>();
	private static final java.util.Set<String> LEFT_STACK = java.util.Set.of("info", "coords", "status", "team");

	private static boolean isDefault(String id, float[] p) {
		float[] d = defaultPos(id);
		return Math.abs(p[0] - d[0]) < 1e-4f && Math.abs(p[1] - d[1]) < 1e-4f;
	}

	/** Screen-space bounds of each element from the last frame: {x, y, w, h}. */
	public static final Map<String, int[]> BOUNDS = new LinkedHashMap<>();

	private Hud() { }

	public static boolean enabled(String id) {
		Config c = FactionsClient.CONFIG;
		return switch (id) {
			case "info" -> c.hudInfo;
			case "coords" -> c.hudCoords;
			case "territory" -> c.hudTerritory && c.claimMap;
			case "status" -> c.hudStatus;
			case "team" -> c.team && c.hudTeamList;
			case "keystrokes" -> c.hudKeystrokes;
			case "effects" -> c.hudEffects;
			case "armor" -> c.hudArmor;
			default -> false;
		};
	}

	public static float[] pos(String id) {
		return FactionsClient.CONFIG.hudPos.computeIfAbsent(id, Hud::defaultPos);
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !FactionsClient.CONFIG.hud || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
		if (mc.screen instanceof HudEditorScreen) return; // the editor draws its own preview
		Modules.sampleClicks(mc);
		drawAll(g, mc, false);
	}

	/** Draws every enabled element at its saved position. preview = show sample data for empty elements. */
	public static void drawAll(GuiGraphics g, Minecraft mc, boolean preview) {
		if (!preview) ChatTabs.renderUnread(g, mc.font);
		BOUNDS.clear();
		int stackY = 3; // left column: elements you haven't moved stack under each other, never overlapping
		for (String id : ELEMENTS.keySet()) {
			if (!enabled(id)) continue;
			float[] p = pos(id);
			int x = Math.round(p[0] * g.guiWidth());
			int y = Math.round(p[1] * g.guiHeight());
			boolean stacked = LEFT_STACK.contains(id) && isDefault(id, p);
			if (stacked) {
				x = 3;
				y = stackY;
			}
			// Never hang off the screen: clamp with this element's size from the last frame.
			int[] last = LAST_SIZE.get(id);
			if (last != null) {
				x = Math.max(0, Math.min(x, g.guiWidth() - last[0]));
				y = Math.max(0, Math.min(y, g.guiHeight() - last[1]));
			}
			float s = p[2];

			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(s, s);
			int[] size = draw(id, g, mc.font, mc, preview);
			g.pose().popMatrix();

			if (size[0] > 0 && size[1] > 0) {
				BOUNDS.put(id, new int[] {x, y, Math.round(size[0] * s), Math.round(size[1] * s)});
				LAST_SIZE.put(id, new int[] {Math.round(size[0] * s), Math.round(size[1] * s)});
				if (stacked) stackY = y + Math.round(size[1] * s) + 3;
			}
		}
	}

	private static int[] draw(String id, GuiGraphics g, Font f, Minecraft mc, boolean preview) {
		LocalPlayer p = mc.player;
		Config cfg = FactionsClient.CONFIG;
		int accent = cfg.accentColor;
		List<Line> lines = LINES; lines.clear();

		switch (id) {
			case "info" -> {
				int ping = 0;
				if (mc.getConnection() != null) {
					PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
					if (info != null) ping = info.getLatency();
				}
				String s = "§7FPS §f" + mc.getFps() + "  §7Ping §f" + ping + "ms";
				if (cfg.crumbs) s += "  §7TNT §f" + Crumbs.liveTnt();
				lines.add(new Line(s, WHITE));
			}
			case "coords" -> {
				lines.add(new Line(String.format(Locale.ROOT, "§7XYZ §f%d %d %d  §7%s", p.getBlockX(), p.getBlockY(), p.getBlockZ(),
						p.getDirection().getSerializedName().toUpperCase(Locale.ROOT)), WHITE));
				if (mc.level != null && mc.level.dimension() == Level.OVERWORLD) {
					lines.add(new Line(String.format(Locale.ROOT, "§cNether §7%d %d", p.getBlockX() / 8, p.getBlockZ() / 8), GRAY));
				} else if (mc.level != null && mc.level.dimension() == Level.NETHER) {
					lines.add(new Line(String.format(Locale.ROOT, "§aOverworld §7%d %d", p.getBlockX() * 8, p.getBlockZ() * 8), GRAY));
				}
			}
			case "territory" -> {
				if (!ServerLink.connected && !preview) break;
				ServerLink.Claim here = ServerLink.claimHere(mc);
				if (here == null && preview && !ServerLink.connected) here = new ServerLink.Claim(ServerLink.OWN, "YourFaction");
				if (here == null) {
					lines.add(new Line("Wilderness", 0xFF9AA3B5));
				} else {
					String what = switch (here.rel()) {
						case ServerLink.SAFEZONE -> "SafeZone";
						case ServerLink.WARZONE -> "WarZone";
						default -> here.tag();
					};
					lines.add(new Line(what, 0xFF000000 | WorldRender.relationColor(here.rel())));
				}
			}
			case "status" -> {
				boolean tSprint = mc.options.toggleSprint().get() || cfg.autoSprint;
				boolean tSneak = mc.options.toggleCrouch().get();
				if (p.isCrouching()) lines.add(new Line("[Sneaking" + (tSneak ? " (Toggled)]" : "]"), WHITE));
				else if (p.isSprinting()) lines.add(new Line("[Sprinting" + (tSprint ? " (Toggled)]" : "]"), WHITE));
				else if (preview) lines.add(new Line("[Sprinting (Toggled)]", WHITE));
			}
			case "team" -> {
				if (!ServerLink.connected && !preview) {
					// Not on RelapsedMC: nothing to show.
				} else {
					String dim = ServerLink.dimId(mc);
					for (ServerLink.Mate m : ServerLink.MATES) {
						String where;
						if (m.dim().equals(dim)) {
							double dx = m.x() - p.getX(), dz = m.z() - p.getZ();
							where = (int) Math.sqrt(dx * dx + dz * dz) + "m " + arrow(p.getYRot(), dx, dz) + "  "
									+ (int) m.x() + " " + (int) m.y() + " " + (int) m.z();
						} else {
							where = "(" + m.dim().replace("minecraft:", "") + ")";
						}
						lines.add(new Line("§f" + m.name() + "  §7" + where, accent));
					}
					ServerLink.Home h = ServerLink.HOME;
					if (cfg.homeBeacon && h != null && h.dim().equals(dim)) {
						double dx = h.x() - p.getX(), dz = h.z() - p.getZ();
						lines.add(new Line("Home  " + (int) Math.sqrt(dx * dx + dz * dz) + "m " + arrow(p.getYRot(), dx, dz), 0xFF4DFF88));
					}
					if (lines.isEmpty() && preview) lines.add(new Line("Teammate  42m ↗  120 64 -300", accent));
				}
			}
			case "keystrokes" -> {
				return keystrokes(g, f, mc, accent);
			}
			case "effects" -> {
				for (MobEffectInstance e : p.getActiveEffects()) {
					String name = e.getEffect().value().getDisplayName().getString();
					if (e.getAmplifier() > 0) name += " " + roman(e.getAmplifier() + 1);
					String time = e.isInfiniteDuration() ? "∞" : fmt(e.getDuration() / 20);
					boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
					int color = ending ? RED : (0xFF000000 | e.getEffect().value().getColor());
					lines.add(new Line(name + "  " + time, color));
				}
				if (lines.isEmpty() && preview) {
					lines.add(new Line("Speed II  1:30", 0xFF33EBFF));
					lines.add(new Line("Fire Resistance  7:59", 0xFFFF9900));
				}
				return rightAligned(g, f, lines);
			}
			case "armor" -> {
				return armor(g, f, p, preview);
			}
			default -> { }
		}
		return leftAligned(g, f, lines);
	}

	// ---- element drawing helpers ----

	/** HUD looks: 0 = glass panel (default), 1 = minimal (text only), 2 = classic (black box per line). */
	static int style() {
		return FactionsClient.CONFIG.hudStyle;
	}

	/** One rounded purple-black glass panel with a thin accent bar on the side the text hugs. */
	static void panel(GuiGraphics g, int x, int y, int w, int h, boolean barRight) {
		int bg = (FactionsClient.CONFIG.hudOpacity * 255 / 100) << 24 | 0x0B0716;
		com.factionsclient.ui.Theme.rect(g, x, y, w, h, bg);
		com.factionsclient.ui.Theme.outline(g, x, y, w, h, 0x1CFFFFFF);
		int bx = barRight ? x + w - 2 : x;
		g.fillGradient(bx, y + 2, bx + 2, y + h - 2, 0xFF38E0FF, 0xFFFF4DD2);
	}

	private static int[] leftAligned(GuiGraphics g, Font f, List<Line> lines) {
		if (lines.isEmpty()) return new int[] {0, 0};
		int w = 0, y = 0;
		for (Line l : lines) w = Math.max(w, f.width(l.text()));
		if (style() == 0) {
			int h = lines.size() * 10 + 5;
			panel(g, 0, 0, w + 11, h, false);
			for (Line l : lines) {
				g.drawString(f, l.text(), 6, y + 3, l.color(), true);
				y += 10;
			}
			return new int[] {w + 11, h};
		}
		for (Line l : lines) {
			if (style() == 2) g.fill(-2, y - 1, f.width(l.text()) + 2, y + 9, BG);
			g.drawString(f, l.text(), 0, y, l.color(), true);
			y += 11;
		}
		return new int[] {w, y};
	}

	private static int[] rightAligned(GuiGraphics g, Font f, List<Line> lines) {
		if (lines.isEmpty()) return new int[] {0, 0};
		int w = 0, y = 0;
		for (Line l : lines) w = Math.max(w, f.width(l.text()));
		if (style() == 0) {
			int h = lines.size() * 10 + 5;
			panel(g, 0, 0, w + 11, h, true);
			for (Line l : lines) {
				g.drawString(f, l.text(), 5 + w - f.width(l.text()), y + 3, l.color(), true);
				y += 10;
			}
			return new int[] {w + 11, h};
		}
		for (Line l : lines) {
			int lw = f.width(l.text());
			if (style() == 2) g.fill(w - lw - 2, y - 1, w + 2, y + 9, BG);
			g.drawString(f, l.text(), w - lw, y, l.color(), true);
			y += 11;
		}
		return new int[] {w, y};
	}

	private static int[] keystrokes(GuiGraphics g, Font f, Minecraft mc, int accent) {
		int k = 18, gap = 2;
		key(g, f, k + gap, 0, k, 18, "W", mc.options.keyUp.isDown(), accent);
		key(g, f, 0, k + gap, k, 18, "A", mc.options.keyLeft.isDown(), accent);
		key(g, f, k + gap, k + gap, k, 18, "S", mc.options.keyDown.isDown(), accent);
		key(g, f, 2 * (k + gap), k + gap, k, 18, "D", mc.options.keyRight.isDown(), accent);
		int full = 3 * k + 2 * gap;
		int half = (full - gap) / 2;
		key(g, f, 0, 2 * (k + gap), half, 18, Modules.leftCps() + " CPS", mc.options.keyAttack.isDown(), accent);
		key(g, f, half + gap, 2 * (k + gap), full - half - gap, 18, Modules.rightCps() + " CPS", mc.options.keyUse.isDown(), accent);
		key(g, f, 0, 3 * (k + gap), full, 10, "", mc.options.keyJump.isDown(), accent);
		return new int[] {full, 3 * (k + gap) + 10};
	}

	private static void key(GuiGraphics g, Font f, int x, int y, int w, int h, String label, boolean down, int accent) {
		if (style() != 2) {
			int bg = (FactionsClient.CONFIG.hudOpacity * 255 / 100) << 24 | 0x0B0716;
			com.factionsclient.ui.Theme.rect(g, x, y, w, h, bg);
			if (down) com.factionsclient.ui.Theme.gradient(g, x, y, w, h, 0.95f);
			com.factionsclient.ui.Theme.outline(g, x, y, w, h, down ? 0x60FFFFFF : 0x1CFFFFFF);
			if (h == 10) {
				g.fill(x + w / 2 - 10, y + 4, x + w / 2 + 10, y + 5, WHITE);
			} else if (f.width(label) > w - 4) {
				com.factionsclient.ui.Theme.scaled(g, f, label, x + (w - f.width(label) * 0.7f) / 2f, y + (h - 5.6f) / 2f, 0.7f, WHITE, down);
			} else {
				g.drawString(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, WHITE, down);
			}
			return;
		}
		g.fill(x, y, x + w, y + h, down ? (0xD0000000 | (accent & 0xFFFFFF)) : BG);
		if (h == 10) {
			g.fill(x + w / 2 - 10, y + 4, x + w / 2 + 10, y + 5, down ? 0xFF000000 : WHITE); // spacebar line
		} else {
			g.drawString(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, down ? 0xFF000000 : WHITE, false);
		}
	}

	private static int[] armor(GuiGraphics g, Font f, LocalPlayer p, boolean preview) {
		int y = 0, w = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = p.getItemBySlot(slot);
			if (stack.isEmpty() && !preview) continue;
			if (!stack.isEmpty()) {
				g.renderItem(stack, 0, y);
				if (stack.isDamageableItem()) {
					int left = stack.getMaxDamage() - stack.getDamageValue();
					String s = String.valueOf(left);
					g.drawString(f, s, 19, y + 4, left * 4 < stack.getMaxDamage() ? RED : WHITE, true);
					w = Math.max(w, 19 + f.width(s));
				}
			} else {
				g.fill(0, y, 16, y + 16, BG); // empty slot placeholder in the editor
			}
			w = Math.max(w, 16);
			y += 17;
		}
		return new int[] {w, y};
	}

	private record Line(String text, int color) { }

	private static final List<Line> LINES = new ArrayList<>();

	private static String fmt(int secs) {
		return String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n < r.length ? r[n] : String.valueOf(n);
	}

	/** Arrow pointing toward (dx, dz) relative to where the player is looking. */
	private static String arrow(float yaw, double dx, double dz) {
		double target = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = ((target - yaw) % 360 + 540) % 360 - 180;
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		return arrows[((int) Math.round(rel / 45.0) + 8) % 8];
	}
}
