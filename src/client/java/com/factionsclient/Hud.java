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
			case "status" -> new float[] {0.005f, 0.135f, 1f};
			case "team" -> new float[] {0.005f, 0.18f, 1f};
			case "keystrokes" -> new float[] {0.005f, 0.62f, 1f};
			case "effects" -> new float[] {0.85f, 0.01f, 1f};
			case "armor" -> new float[] {0.90f, 0.70f, 1f};
			default -> new float[] {0.1f, 0.1f, 1f};
		};
	}

	/** Screen-space bounds of each element from the last frame: {x, y, w, h}. */
	public static final Map<String, int[]> BOUNDS = new LinkedHashMap<>();

	private Hud() { }

	public static boolean enabled(String id) {
		Config c = FactionsClient.CONFIG;
		return switch (id) {
			case "info" -> c.hudInfo;
			case "coords" -> c.hudCoords;
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
		BOUNDS.clear();
		for (String id : ELEMENTS.keySet()) {
			if (!enabled(id)) continue;
			float[] p = pos(id);
			int x = Math.round(p[0] * g.guiWidth());
			int y = Math.round(p[1] * g.guiHeight());
			float s = p[2];

			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(s, s);
			int[] size = draw(id, g, mc.font, mc, preview);
			g.pose().popMatrix();

			if (size[0] > 0 && size[1] > 0) {
				BOUNDS.put(id, new int[] {x, y, Math.round(size[0] * s), Math.round(size[1] * s)});
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
				String s = "FPS " + mc.getFps() + "  Ping " + ping + "ms";
				if (cfg.crumbs) s += "  TNT " + Crumbs.liveTnt();
				lines.add(new Line(s, accent));
			}
			case "coords" -> {
				lines.add(new Line(String.format(Locale.ROOT, "XYZ %d %d %d  %s", p.getBlockX(), p.getBlockY(), p.getBlockZ(),
						p.getDirection().getSerializedName().toUpperCase(Locale.ROOT)), WHITE));
				if (mc.level != null && mc.level.dimension() == Level.OVERWORLD) {
					lines.add(new Line(String.format(Locale.ROOT, "Nether %d %d", p.getBlockX() / 8, p.getBlockZ() / 8), GRAY));
				} else if (mc.level != null && mc.level.dimension() == Level.NETHER) {
					lines.add(new Line(String.format(Locale.ROOT, "Overworld %d %d", p.getBlockX() * 8, p.getBlockZ() * 8), GRAY));
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
						lines.add(new Line(m.name() + "  " + where, accent));
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

	private static int[] leftAligned(GuiGraphics g, Font f, List<Line> lines) {
		int w = 0, y = 0;
		for (Line l : lines) w = Math.max(w, f.width(l.text()));
		for (Line l : lines) {
			g.fill(-2, y - 1, f.width(l.text()) + 2, y + 9, BG);
			g.drawString(f, l.text(), 0, y, l.color(), true);
			y += 11;
		}
		return new int[] {w, y};
	}

	private static int[] rightAligned(GuiGraphics g, Font f, List<Line> lines) {
		int w = 0, y = 0;
		for (Line l : lines) w = Math.max(w, f.width(l.text()));
		for (Line l : lines) {
			int lw = f.width(l.text());
			g.fill(w - lw - 2, y - 1, w + 2, y + 9, BG);
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
