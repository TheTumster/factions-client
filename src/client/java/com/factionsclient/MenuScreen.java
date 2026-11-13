package com.factionsclient;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Right Shift menu: toggle modules on/off. */
public class MenuScreen extends Screen {
	public MenuScreen() {
		super(Component.literal("Factions Client"));
	}

	@Override
	protected void init() {
		Config c = FactionsClient.CONFIG;
		int w = 150;
		int left = this.width / 2 - w - 5;
		int right = this.width / 2 + 5;
		int y = this.height / 4;

		toggle(left, y, w, "HUD", () -> c.hud, v -> c.hud = v);
		toggle(right, y, w, "TNT/Sand Crumbs", () -> c.crumbs, v -> c.crumbs = v);
		y += 24;
		toggle(left, y, w, "Team Tracking", () -> c.team, v -> c.team = v);
		toggle(right, y, w, "Share Patch Crumbs", () -> c.shareCrumbs, v -> c.shareCrumbs = v);
		y += 24;
		toggle(left, y, w, "HUD: Coords", () -> c.hudCoords, v -> c.hudCoords = v);
		toggle(right, y, w, "HUD: Armor", () -> c.hudArmor, v -> c.hudArmor = v);
		y += 24;
		toggle(left, y, w, "HUD: Effects", () -> c.hudEffects, v -> c.hudEffects = v);
		toggle(right, y, w, "HUD: Team List", () -> c.hudTeamList, v -> c.hudTeamList = v);
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Crumb time: " + c.crumbSeconds + "s"), b -> {
			c.crumbSeconds = c.crumbSeconds >= 60 ? 5 : c.crumbSeconds + 5;
			b.setMessage(Component.literal("Crumb time: " + c.crumbSeconds + "s"));
			c.save();
		}).bounds(left, y, w, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Clear Crumbs"), b -> Crumbs.clear())
				.bounds(right, y, w, 20).build());
		y += 36;

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(this.width / 2 - 100, y, 200, 20).build());
	}

	private void toggle(int x, int y, int w, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
		addRenderableWidget(Button.builder(text(label, get.get()), b -> {
			set.accept(!get.get());
			b.setMessage(text(label, get.get()));
			FactionsClient.CONFIG.save();
		}).bounds(x, y, w, 20).build());
	}

	private static Component text(String label, boolean on) {
		return Component.literal(label + ": " + (on ? "§aON" : "§cOFF"));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 - 20, 0xFF38E0FF);
		String status = FactionsClient.CONFIG.backendReady()
				? (TeamSync.lastError == null ? "Backend: connected" : "Backend error: " + TeamSync.lastError)
				: "Backend: not set up. Edit config/factionsclient.json, then /fc reload";
		g.drawCenteredString(this.font, Component.literal(status), this.width / 2, this.height - 20, 0xFFAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
