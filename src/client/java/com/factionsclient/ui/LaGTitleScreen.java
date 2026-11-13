package com.factionsclient.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import net.fabricmc.loader.api.FabricLoader;

import com.factionsclient.Updater;

/** The laG main menu: blurred panorama, glowing logo, gradient buttons. */
public class LaGTitleScreen extends Screen {
	private final List<Theme.Btn> buttons = new ArrayList<>();
	private final long openedAt = System.currentTimeMillis();
	private static String version;

	public LaGTitleScreen() {
		super(Component.literal(Theme.BRAND));
	}

	@Override
	protected void init() {
		buttons.clear();
		int bw = 200, bh = 22, gap = 6;
		int cx = this.width / 2;
		int y = this.height / 2 - 4;

		buttons.add(new Theme.Btn("SINGLEPLAYER", () -> minecraft.setScreen(new SelectWorldScreen(this))).primary().at(cx - bw / 2, y, bw, bh));
		y += bh + gap;
		buttons.add(new Theme.Btn("MULTIPLAYER", () -> minecraft.setScreen(new JoinMultiplayerScreen(this))).primary().at(cx - bw / 2, y, bw, bh));
		y += bh + gap;
		int half = (bw - gap) / 2;
		buttons.add(new Theme.Btn("Options", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))).at(cx - bw / 2, y, half, bh));
		buttons.add(new Theme.Btn("laG Menu", () -> minecraft.setScreen(new LaGMenu(this))).at(cx - bw / 2 + half + gap, y, half, bh));
		y += bh + gap;
		buttons.add(new Theme.Btn("Quit", () -> minecraft.stop()).at(cx - bw / 2, y, bw, bh));

		if (version == null) {
			version = FabricLoader.getInstance().getModContainer("factionsclient")
					.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderPanorama(g, delta);
		this.renderBlurredBackground(g);
		g.fill(0, 0, this.width, this.height, 0x8007090D);
		Theme.vignette(g, this.width, this.height, 1f);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Theme.tickFrame();
		super.render(g, mouseX, mouseY, delta);

		float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 700f);
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);

		// Logo with a slow breathing glow
		int lw = Math.min(300, this.width - 40);
		int lh = lw * 384 / 1024;
		int ly = this.height / 2 - lh - 16 - Math.round((1 - ease) * 10);
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 900.0);
		Theme.glow(g, this.width / 2 - lw / 4, ly + lh / 4, lw / 2, lh / 3, ease * (0.25f + 0.2f * pulse));
		Theme.logo(g, this.width / 2 - lw / 2, ly, lw, ease);

		g.pose().pushMatrix();
		g.pose().translate(0, (1 - ease) * 14);
		for (Theme.Btn b : buttons) b.render(g, this.font, mouseX, mouseY, ease);
		g.pose().popMatrix();

		String left = Theme.BRAND + " " + version + "  •  Minecraft 1.21.11";
		g.drawString(this.font, left, 6, this.height - 12, Theme.alpha(Theme.MUTED, ease), false);
		if (Updater.pending != null) {
			String u = "⬇ Update ready, restart to apply";
			g.drawString(this.font, u, this.width - this.font.width(u) - 6, this.height - 12, Theme.alpha(0xFF6CFFB0, ease), false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.button() == 0) {
			for (Theme.Btn b : buttons) {
				if (b.click(e.x(), e.y())) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					return true;
				}
			}
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
