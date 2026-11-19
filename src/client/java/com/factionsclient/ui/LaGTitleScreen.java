package com.factionsclient.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerSkin;

import net.fabricmc.loader.api.FabricLoader;

import com.factionsclient.RelapsedServer;
import com.factionsclient.Updater;

/**
 * RelapsedMC main menu: blurred panorama under a purple haze with drifting embers, the server wordmark,
 * a big one-click PLAY RELAPSEDMC button with live player count, and your skin in the corner.
 */
public class LaGTitleScreen extends Screen {
	private final List<Theme.Btn> buttons = new ArrayList<>();
	private final long openedAt = System.currentTimeMillis();
	private static String version;
	private Theme.Btn play;
	private Supplier<PlayerSkin> skin;

	/** Drifting embers: x, y (0..1 of screen), speed, size, color t, phase. */
	private static final int EMBERS = 46;
	private final float[][] embers = new float[EMBERS][6];

	public LaGTitleScreen() {
		super(Component.literal(Theme.BRAND));
		Random r = new Random();
		for (float[] e : embers) seed(e, r, true);
	}

	private static void seed(float[] e, Random r, boolean anywhere) {
		e[0] = r.nextFloat();
		e[1] = anywhere ? r.nextFloat() : 1.05f;
		e[2] = 0.010f + r.nextFloat() * 0.025f;
		e[3] = 1 + r.nextInt(2);
		e[4] = r.nextFloat();
		e[5] = r.nextFloat() * 6.28f;
	}

	private int logoTop() {
		return Math.max(10, this.height / 2 - 118);
	}

	private int logoWidth() {
		return Math.min(340, this.width - 60);
	}

	@Override
	protected void init() {
		buttons.clear();
		int cx = this.width / 2;
		int bw = 232, gap = 6, bh = 22;
		int lh = logoWidth() * 384 / 1024;
		int y = Math.min(logoTop() + lh + 8, this.height - (36 + 2 * (bh + gap) + 40));

		play = new Theme.Btn("PLAY RELAPSEDMC", () -> RelapsedServer.join(this)).primary().at(cx - bw / 2, y, bw, 36);
		buttons.add(play);
		y += 36 + gap + 2;

		int half = (bw - gap) / 2;
		buttons.add(new Theme.Btn("Singleplayer", () -> minecraft.setScreen(new SelectWorldScreen(this))).at(cx - bw / 2, y, half, bh));
		buttons.add(new Theme.Btn("Multiplayer", () -> minecraft.setScreen(new JoinMultiplayerScreen(this))).at(cx - bw / 2 + half + gap, y, half, bh));
		y += bh + gap;

		int third = (bw - 2 * gap) / 3;
		buttons.add(new Theme.Btn("Options", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))).at(cx - bw / 2, y, third, bh));
		buttons.add(new Theme.Btn("Client", () -> minecraft.setScreen(new LaGMenu(this))).at(cx - bw / 2 + third + gap, y, third, bh));
		buttons.add(new Theme.Btn("Quit", () -> minecraft.stop()).at(cx - bw / 2 + 2 * (third + gap), y, bw - 2 * (third + gap), bh));

		if (version == null) {
			version = FabricLoader.getInstance().getModContainer("factionsclient")
					.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		}
		if (skin == null) {
			try {
				skin = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false);
			} catch (Exception ignored) {
				// offline/dev: no face, that's fine
			}
		}
	}

	@Override
	public void removed() {
		RelapsedServer.stop();
		super.removed();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderPanorama(g, delta);
		this.renderBlurredBackground(g);
		// Purple haze: darker at the bottom where the buttons sit
		g.fillGradient(0, 0, this.width, this.height, 0x900B0716, 0xE00B0716);
		long now = System.currentTimeMillis();
		// Two slow aurora glows behind the logo
		float a1 = 0.18f + 0.08f * (float) Math.sin(now / 2300.0);
		float a2 = 0.14f + 0.08f * (float) Math.sin(now / 1700.0 + 2);
		int lw = logoWidth();
		int ly = logoTop();
		int drift = (int) (Math.sin(now / 4000.0) * 30);
		Theme.glow(g, this.width / 2 - lw / 2 - 20 + drift, ly, lw / 2, lw / 4, a1);
		Theme.glow(g, this.width / 2 + 20 - drift, ly + 10, lw / 2, lw / 4, a2);
		embers(g);
		Theme.vignette(g, this.width, this.height, 1f);
	}

	private long lastEmberNanos = System.nanoTime();

	private void embers(GuiGraphics g) {
		long nowN = System.nanoTime();
		float dt = Math.min(0.1f, (nowN - lastEmberNanos) / 1_000_000_000f);
		lastEmberNanos = nowN;
		Random r = new Random();
		long now = System.currentTimeMillis();
		for (float[] e : embers) {
			e[1] -= e[2] * dt;
			if (e[1] < -0.05f) seed(e, r, false);
			float sway = (float) Math.sin(now / 1200.0 + e[5]) * 0.004f;
			int x = (int) ((e[0] + sway) * this.width);
			int y = (int) (e[1] * this.height);
			float fade = Math.min(1f, Math.min(e[1] * 3f, (1.05f - e[1]) * 4f));
			float twinkle = 0.55f + 0.45f * (float) Math.sin(now / 500.0 + e[5] * 3);
			int col = Theme.alpha(Theme.gradientAt(e[4]), Math.max(0, fade) * twinkle * 0.8f);
			int s = (int) e[3];
			g.fill(x, y, x + s, y + s, col);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Theme.tickFrame();
		RelapsedServer.tick();
		super.render(g, mouseX, mouseY, delta);

		long now = System.currentTimeMillis();
		float t = Math.min(1f, (now - openedAt) / 800f);
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);

		// Logo
		int lw = logoWidth();
		int ly = logoTop() - Math.round((1 - ease) * 10);
		Theme.logo(g, this.width / 2 - lw / 2, ly, lw, ease);

		// Live status under PLAY
		switch (RelapsedServer.state()) {
			case ONLINE -> {
				String on = RelapsedServer.onlineText();
				long ms = RelapsedServer.ping();
				play.sub = (on != null ? on : "Online") + (ms >= 0 ? "  ·  " + ms + "ms" : "");
				play.subColor = 0xFF4DFF88;
			}
			case OFFLINE -> {
				play.sub = "Server offline";
				play.subColor = 0xFFFF3B4E;
			}
			default -> {
				play.sub = RelapsedServer.IP;
				play.subColor = 0xFFFFFFFF;
			}
		}

		// Buttons slide up one after another
		for (int i = 0; i < buttons.size(); i++) {
			float bt = Math.max(0f, Math.min(1f, (now - openedAt - 120 - i * 45) / 520f));
			float be = 1f - (1f - bt) * (1f - bt) * (1f - bt);
			g.pose().pushMatrix();
			g.pose().translate(0, (1 - be) * 12);
			buttons.get(i).render(g, this.font, mouseX, mouseY, be);
			g.pose().popMatrix();
		}

		// You, top right (click to switch accounts)
		String name = minecraft.getUser().getName();
		boolean offline = com.factionsclient.Accounts.activeOffline() != null;
		String tag = offline ? "  offline" : "";
		int nw = this.font.width(name) + this.font.width(tag) + 10;
		chipW = nw + 30;
		chipX = this.width - chipW - 8;
		int chipY = 8;
		boolean chipOver = Theme.inside(mouseX, mouseY, chipX, chipY, chipW, 20);
		Theme.rect(g, chipX, chipY, chipW, 20, Theme.alpha(chipOver ? 0xE0241A3A : 0xC0161026, ease));
		Theme.outline(g, chipX, chipY, chipW, 20, Theme.alpha(chipOver ? 0x90FFFFFF : 0x40FFFFFF, ease));
		if (offline) {
			PlayerFaceRenderer.draw(g, net.minecraft.client.resources.DefaultPlayerSkin.get(minecraft.getUser().getProfileId()), chipX + 4, chipY + 4, 12);
		} else if (skin != null) {
			PlayerFaceRenderer.draw(g, skin.get(), chipX + 4, chipY + 4, 12);
		}
		g.drawString(this.font, name, chipX + 22, chipY + 6, Theme.alpha(Theme.TEXT, ease), false);
		if (offline) g.drawString(this.font, tag, chipX + 22 + this.font.width(name), chipY + 6, Theme.alpha(0xFFFFB04D, ease), false);
		g.drawString(this.font, "▾", chipX + chipW - 10, chipY + 6, Theme.alpha(Theme.MUTED, ease), false);

		// Footer
		String left = Theme.BRAND + " " + version + "  ·  Minecraft 1.21.11";
		g.drawString(this.font, left, 6, this.height - 12, Theme.alpha(Theme.MUTED, ease), false);
		String right = Updater.pending != null ? "⬇ Update ready, restart to apply" : RelapsedServer.IP;
		int rc = Updater.pending != null ? 0xFF6CFFB0 : Theme.DIM;
		g.drawString(this.font, right, this.width - this.font.width(right) - 6, this.height - 12, Theme.alpha(rc, ease), false);
		Theme.gradientLine(g, 0, this.height - 1, this.width, 1, 0.6f * ease);
	}

	private int chipX, chipW;

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.button() == 0 && chipW > 0 && Theme.inside(e.x(), e.y(), chipX, 8, chipW, 20)) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			minecraft.setScreen(new AccountScreen(this));
			return true;
		}
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
