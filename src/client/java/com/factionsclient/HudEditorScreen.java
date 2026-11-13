package com.factionsclient;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Drag HUD elements around. Click one to select it, then use - / + to resize. */
public class HudEditorScreen extends Screen {
	private final Screen parent;
	private String selected = null;
	private String dragging = null;
	private int grabDX, grabDY;
	private boolean wasDown = false;
	private Button nameBtn;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("HUD Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = this.height - 26;
		int cx = this.width / 2;
		nameBtn = addRenderableWidget(Button.builder(label(), b -> { }).bounds(cx - 190, y, 120, 20).build());
		addRenderableWidget(Button.builder(Component.literal("-"), b -> scale(-0.1f)).bounds(cx - 66, y, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> scale(0.1f)).bounds(cx - 44, y, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
			if (selected != null) FactionsClient.CONFIG.hudPos.put(selected, Hud.defaultPos(selected));
			FactionsClient.CONFIG.save();
		}).bounds(cx - 20, y, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset All"), b -> {
			FactionsClient.CONFIG.hudPos.clear();
			FactionsClient.CONFIG.save();
		}).bounds(cx + 34, y, 64, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(cx + 102, y, 88, 20).build());
	}

	private Component label() {
		if (selected == null) return Component.literal("Click an element");
		return Component.literal(Hud.ELEMENTS.get(selected) + " " + Math.round(Hud.pos(selected)[2] * 100) + "%");
	}

	private void scale(float d) {
		if (selected == null) return;
		float[] p = Hud.pos(selected);
		p[2] = Math.max(0.5f, Math.min(3f, Math.round((p[2] + d) * 10f) / 10f));
		FactionsClient.CONFIG.save();
		nameBtn.setMessage(label());
	}

	private boolean overWidget(double mx, double my) {
		for (GuiEventListener l : this.children()) {
			if (l.isMouseOver(mx, my)) return true;
		}
		return false;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Minecraft mc = Minecraft.getInstance();
		boolean down = mc.mouseHandler.isLeftPressed();

		// Start a drag on the frame the mouse goes down over an element
		if (down && !wasDown && !overWidget(mouseX, mouseY)) {
			dragging = null;
			for (Map.Entry<String, int[]> e : Hud.BOUNDS.entrySet()) {
				int[] b = e.getValue();
				if (mouseX >= b[0] - 2 && mouseX <= b[0] + b[2] + 2 && mouseY >= b[1] - 2 && mouseY <= b[1] + b[3] + 2) {
					dragging = e.getKey();
					selected = e.getKey();
					grabDX = mouseX - b[0];
					grabDY = mouseY - b[1];
					nameBtn.setMessage(label());
				}
			}
		}
		if (down && dragging != null) {
			int[] b = Hud.BOUNDS.get(dragging);
			int w = b == null ? 0 : b[2], h = b == null ? 0 : b[3];
			int nx = mouseX - grabDX, ny = mouseY - grabDY;
			// Snap to screen edges
			if (Math.abs(nx - 2) < 6) nx = 2;
			if (Math.abs(ny - 2) < 6) ny = 2;
			if (Math.abs(nx + w - (this.width - 2)) < 6) nx = this.width - 2 - w;
			if (Math.abs(ny + h - (this.height - 2)) < 6) ny = this.height - 2 - h;
			nx = Math.max(0, Math.min(this.width - w, nx));
			ny = Math.max(0, Math.min(this.height - h, ny));
			float[] p = Hud.pos(dragging);
			p[0] = (float) nx / this.width;
			p[1] = (float) ny / this.height;
		}
		if (!down && wasDown && dragging != null) {
			dragging = null;
			FactionsClient.CONFIG.save();
		}
		wasDown = down;

		// Dim background, then the live HUD with sample data, then outlines
		g.fill(0, 0, this.width, this.height, 0x60000000);
		if (mc.player != null) Hud.drawAll(g, mc, true);
		for (Map.Entry<String, int[]> e : Hud.BOUNDS.entrySet()) {
			int[] b = e.getValue();
			int col = e.getKey().equals(selected) ? FactionsClient.CONFIG.accentColor : 0x80FFFFFF;
			outline(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, col);
		}
		g.drawCenteredString(this.font, "Drag elements to move them. Turn elements on/off in Right Shift > HUD.", this.width / 2, this.height - 40, 0xFFDDDDDD);
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// no blur/dirt: we want to see the game behind the HUD
	}

	private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
		g.fill(x, y, x + w, y + 1, c);
		g.fill(x, y + h - 1, x + w, y + h, c);
		g.fill(x, y, x + 1, y + h, c);
		g.fill(x + w - 1, y, x + w, y + h, c);
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
