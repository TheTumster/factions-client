package com.factionsclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.factionsclient.ui.Theme;

/**
 * Drag HUD elements anywhere. Click to select, scroll (or - / +) to resize, snaps to edges and
 * to the center lines (guides show when snapped). Arrow keys nudge the selected element.
 */
public class HudEditorScreen extends Screen {
	private static final int SNAP = 5;

	private final Screen parent;
	private String selected = null;
	private String dragging = null;
	private double grabDX, grabDY;
	private boolean snapX, snapY, snapCX, snapCY;
	private final List<Theme.Btn> bar = new ArrayList<>();

	public HudEditorScreen(Screen parent) {
		super(Component.literal("HUD Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		bar.clear();
		int bh = 18, y = this.height - bh - 8;
		int x = this.width / 2 - 170;
		bar.add(new Theme.Btn("−", () -> scale(-0.1f)).at(x + 130, y, 20, bh));
		bar.add(new Theme.Btn("+", () -> scale(0.1f)).at(x + 152, y, 20, bh));
		bar.add(new Theme.Btn("Reset", () -> {
			if (selected != null) FactionsClient.CONFIG.hudPos.put(selected, Hud.defaultPos(selected));
		}).at(x + 176, y, 50, bh));
		bar.add(new Theme.Btn("Reset All", () -> FactionsClient.CONFIG.hudPos.clear()).at(x + 230, y, 60, bh));
		bar.add(new Theme.Btn("Done", this::onClose).primary().at(x + 294, y, 46, bh));
	}

	private void scale(float d) {
		if (selected == null) return;
		float[] p = Hud.pos(selected);
		p[2] = Math.max(0.5f, Math.min(3f, Math.round((p[2] + d) * 10f) / 10f));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Keep the world visible so you can place things over the real game.
		if (Minecraft.getInstance().level == null) {
			this.renderPanorama(g, delta);
			this.renderBlurredBackground(g);
			g.fill(0, 0, this.width, this.height, 0xA00B0716);
		} else {
			g.fill(0, 0, this.width, this.height, 0x50000000);
		}
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		Theme.tickFrame();
		super.render(g, mx, my, delta);
		Minecraft mc = Minecraft.getInstance();

		// Center guides
		if (dragging != null) {
			if (snapCX) g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x90B040FF);
			if (snapCY) g.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x9038E0FF);
		}

		if (mc.player != null) {
			Hud.drawAll(g, mc, true);
		} else {
			// Opened from the main menu: there's no game to lay the HUD over yet.
			Hud.BOUNDS.clear();
			String l1 = "Join a world or server to edit your HUD";
			String l2 = "Then press Right Shift  ›  HUD  ›  Edit HUD Layout";
			com.factionsclient.ui.Theme.scaled(g, this.font, l1, this.width / 2f - this.font.width(l1) * 0.7f, this.height / 2f - 16, 1.4f, 0xFFFFFFFF, true);
			g.drawCenteredString(this.font, l2, this.width / 2, this.height / 2 + 4, com.factionsclient.ui.Theme.MUTED);
		}

		for (Map.Entry<String, int[]> e : Hud.BOUNDS.entrySet()) {
			int[] b = e.getValue();
			boolean sel = e.getKey().equals(selected);
			boolean over = Theme.inside(mx, my, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6);
			if (sel) {
				Theme.glow(g, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6, 0.35f);
				Theme.outline(g, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6, 0xFFB040FF);
			} else {
				Theme.outline(g, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6, over ? 0xA0FFFFFF : 0x40FFFFFF);
			}
		}

		// Toolbar
		int y = this.height - 26, x = this.width / 2 - 178;
		Theme.glow(g, x, y - 4, 356, 26, 0.25f);
		Theme.rect(g, x, y - 4, 356, 26, Theme.PANEL);
		Theme.gradientLine(g, x, y - 4, 356, 1, 1f);
		String name = selected == null ? "Click an element" : Hud.ELEMENTS.get(selected) + "  " + Math.round(Hud.pos(selected)[2] * 100) + "%";
		g.drawString(font, Theme.trim(font, name, 120), x + 8, y + 1, selected == null ? Theme.MUTED : Theme.TEXT, false);
		for (Theme.Btn b : bar) b.render(g, font, mx, my, 1f);

		if (mc.player != null && dragging == null) {
			String hint = "Drag to move  •  Scroll to resize  •  Arrow keys to nudge";
			int hw = font.width(hint) + 12, hx = this.width / 2 - hw / 2, hy = y - 22;
			Theme.rect(g, hx, hy, hw, 14, 0xC00B0716);
			g.drawString(font, hint, hx + 6, hy + 3, 0xFFB8B0CC, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x(), my = e.y();
		if (e.button() == 0) {
			for (Theme.Btn b : bar) if (b.click(mx, my)) return true;
		}
		// Topmost element under the cursor
		String hit = null;
		for (Map.Entry<String, int[]> en : Hud.BOUNDS.entrySet()) {
			int[] b = en.getValue();
			if (Theme.inside(mx, my, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6)) hit = en.getKey();
		}
		if (hit != null) {
			selected = hit;
			if (e.button() == 0) {
				int[] b = Hud.BOUNDS.get(hit);
				dragging = hit;
				grabDX = mx - b[0];
				grabDY = my - b[1];
			}
			return true;
		}
		selected = null;
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(e, dx, dy);
		int[] b = Hud.BOUNDS.get(dragging);
		int w = b == null ? 0 : b[2], h = b == null ? 0 : b[3];
		double nx = e.x() - grabDX, ny = e.y() - grabDY;
		snapX = snapY = snapCX = snapCY = false;

		if (Math.abs(nx - 2) < SNAP) { nx = 2; snapX = true; }
		if (Math.abs(nx + w - (this.width - 2)) < SNAP) { nx = this.width - 2 - w; snapX = true; }
		if (Math.abs(nx + w / 2.0 - this.width / 2.0) < SNAP) { nx = this.width / 2.0 - w / 2.0; snapCX = true; }
		if (Math.abs(ny - 2) < SNAP) { ny = 2; snapY = true; }
		if (Math.abs(ny + h - (this.height - 2)) < SNAP) { ny = this.height - 2 - h; snapY = true; }
		if (Math.abs(ny + h / 2.0 - this.height / 2.0) < SNAP) { ny = this.height / 2.0 - h / 2.0; snapCY = true; }

		nx = Math.max(0, Math.min(this.width - w, nx));
		ny = Math.max(0, Math.min(this.height - h, ny));
		float[] p = Hud.pos(dragging);
		p[0] = (float) (nx / this.width);
		p[1] = (float) (ny / this.height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging != null) {
			dragging = null;
			FactionsClient.CONFIG.save();
			return true;
		}
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		String target = selected;
		for (Map.Entry<String, int[]> en : Hud.BOUNDS.entrySet()) {
			int[] b = en.getValue();
			if (Theme.inside(mx, my, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6)) target = en.getKey();
		}
		if (target == null) return false;
		selected = target;
		scale(v > 0 ? 0.1f : -0.1f);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (selected != null) {
			float[] p = Hud.pos(selected);
			float stepX = 1f / this.width, stepY = 1f / this.height;
			if (e.isLeft()) { p[0] = Math.max(0, p[0] - stepX); return true; }
			if (e.isRight()) { p[0] = Math.min(1, p[0] + stepX); return true; }
			if (e.isUp()) { p[1] = Math.max(0, p[1] - stepY); return true; }
			if (e.isDown()) { p[1] = Math.min(1, p[1] + stepY); return true; }
		}
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		FactionsClient.CONFIG.save();
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
