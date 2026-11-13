package com.factionsclient;

import java.util.Locale;

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

public final class Hud {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int ACCENT = 0xFF38E0FF;
	private static final int RED = 0xFFFF5555;
	private static final int BG = 0x90000000;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private Hud() { }

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		Config cfg = FactionsClient.CONFIG;
		if (p == null || !cfg.hud || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;

		Font f = mc.font;
		int x = 4;
		int y = 4;

		// Title line: FPS / ping / live TNT
		int ping = 0;
		if (mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
			if (info != null) ping = info.getLatency();
		}
		String head = "FPS " + mc.getFps() + "  Ping " + ping + "ms";
		if (cfg.crumbs) head += "  TNT " + Crumbs.liveTnt();
		y = line(g, f, x, y, head, ACCENT);

		if (cfg.hudCoords) {
			y = line(g, f, x, y, String.format(Locale.ROOT, "XYZ %d %d %d  %s",
					p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.getDirection().getSerializedName().toUpperCase(Locale.ROOT)), WHITE);
			if (mc.level != null) {
				if (mc.level.dimension() == Level.OVERWORLD) {
					y = line(g, f, x, y, String.format(Locale.ROOT, "Nether %d %d", p.getBlockX() / 8, p.getBlockZ() / 8), GRAY);
				} else if (mc.level.dimension() == Level.NETHER) {
					y = line(g, f, x, y, String.format(Locale.ROOT, "Overworld %d %d", p.getBlockX() * 8, p.getBlockZ() * 8), GRAY);
				}
			}
		}

		if (cfg.team && cfg.hudTeamList) {
			if (!cfg.backendReady()) {
				y = line(g, f, x, y, "Team: not set up (/fc help)", GRAY);
			} else if (TeamSync.lastError != null) {
				y = line(g, f, x, y, "Team: sync error (see log)", RED);
			} else {
				String dim = TeamSync.dimId(mc);
				for (TeamSync.Mate m : TeamSync.MATES) {
					String where;
					if (m.dim().equals(dim)) {
						double dx = m.x() - p.getX();
						double dz = m.z() - p.getZ();
						int dist = (int) Math.sqrt(dx * dx + dz * dz);
						where = dist + "m " + arrow(p.getYRot(), dx, dz) + "  " + (int) m.x() + " " + (int) m.y() + " " + (int) m.z();
					} else {
						where = "(" + m.dim().replace("minecraft:", "") + ")";
					}
					y = line(g, f, x, y, m.name() + "  " + where, ACCENT);
				}
			}
		}

		if (cfg.hudEffects) {
			int ey = 4;
			int right = g.guiWidth() - 4;
			for (MobEffectInstance e : p.getActiveEffects()) {
				String name = e.getEffect().value().getDisplayName().getString();
				if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
				String time = e.isInfiniteDuration() ? "**:**" : fmt(e.getDuration() / 20);
				String s = name + " " + time;
				int w = f.width(s);
				g.fill(right - w - 2, ey - 1, right + 2, ey + 9, BG);
				g.drawString(f, s, right - w, ey, e.getDuration() < 200 && !e.isInfiniteDuration() ? RED : WHITE, true);
				ey += 11;
			}
		}

		if (cfg.hudArmor) {
			// Stack armor bottom-right, with durability left
			int ax = g.guiWidth() - 60;
			int ay = g.guiHeight() - 4 - 4 * 17;
			for (EquipmentSlot slot : ARMOR) {
				ItemStack stack = p.getItemBySlot(slot);
				if (!stack.isEmpty()) {
					g.renderItem(stack, ax, ay);
					if (stack.isDamageableItem()) {
						int left = stack.getMaxDamage() - stack.getDamageValue();
						int color = left * 4 < stack.getMaxDamage() ? RED : WHITE;
						g.drawString(f, String.valueOf(left), ax + 19, ay + 4, color, true);
					}
				}
				ay += 17;
			}
		}
	}

	private static int line(GuiGraphics g, Font f, int x, int y, String s, int color) {
		g.fill(x - 2, y - 1, x + f.width(s) + 2, y + 9, BG);
		g.drawString(f, s, x, y, color, true);
		return y + 11;
	}

	private static String fmt(int secs) {
		return String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}

	/** Arrow pointing toward (dx, dz) relative to where the player is looking. */
	private static String arrow(float yaw, double dx, double dz) {
		double target = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = ((target - yaw) % 360 + 540) % 360 - 180; // -180..180, 0 = straight ahead
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		int idx = (int) Math.round(rel / 45.0);
		return arrows[(idx + 8) % 8];
	}
}
