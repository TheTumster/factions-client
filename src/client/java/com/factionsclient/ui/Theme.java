package com.factionsclient.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** laG look: dark glass panels, cyan -> purple gradient, soft glow. All drawing helpers live here. */
public final class Theme {
	public static final String BRAND = "RelapsedMC Client";

	public static final Identifier GRADIENT = id("textures/gui/gradient.png");
	public static final Identifier GLOW = id("textures/gui/glow.png");
	public static final Identifier LOGO = id("textures/gui/logo.png");
	public static final Identifier VIGNETTE = id("textures/gui/vignette.png");

	public static final int CYAN = 0xFF38E0FF;
	public static final int PURPLE = 0xFFB040FF;
	public static final int BG = 0xF00A0D12;
	public static final int PANEL = 0xF20E1219;
	public static final int SIDEBAR = 0xF5090C11;
	public static final int CARD = 0xFF141A23;
	public static final int CARD_HOVER = 0xFF1A2230;
	public static final int FIELD = 0xFF0B0F15;
	public static final int LINE = 0x1FFFFFFF;
	public static final int TEXT = 0xFFEEF2F7;
	public static final int MUTED = 0xFF8A94A6;
	public static final int DIM = 0xFF5A6374;

	private static long lastFrame = System.nanoTime();
	private static float dt = 0.016f;

	private Theme() { }

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("factionsclient", path);
	}

	/** Call once per frame from a screen's render; gives smooth, framerate-independent animations. */
	public static void tickFrame() {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
	}

	/** Eases `cur` toward `target`. speed ~10 = snappy. */
	public static float approach(float cur, float target, float speed) {
		return cur + (target - cur) * Math.min(1f, speed * dt);
	}

	public static int alpha(int argb, float a) {
		int base = (argb >>> 24) & 0xFF;
		return ((int) (base * Math.max(0, Math.min(1, a))) << 24) | (argb & 0xFFFFFF);
	}

	public static int white(float a) {
		return alpha(0xFFFFFFFF, a);
	}

	public static int lerp(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24);
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24);
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
				| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	/** Color along the logo gradient, t in 0..1. */
	public static int gradientAt(float t) {
		return lerp(CYAN, PURPLE, t);
	}

	/** Filled rectangle with 1px rounded corners. */
	public static void rect(GuiGraphics g, int x, int y, int w, int h, int c) {
		if (w <= 2 || h <= 2) {
			g.fill(x, y, x + w, y + h, c);
			return;
		}
		g.fill(x + 1, y, x + w - 1, y + h, c);
		g.fill(x, y + 1, x + 1, y + h - 1, c);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
	}

	/** Rounded rectangle filled with the cyan -> purple gradient. */
	public static void gradient(GuiGraphics g, int x, int y, int w, int h, float a) {
		if (w <= 0 || h <= 0 || a <= 0.01f) return;
		int col = white(a);
		if (w <= 2 || h <= 2) {
			g.blit(RenderPipelines.GUI_TEXTURED, GRADIENT, x, y, 0f, 0f, w, h, 256, 16, 256, 16, col);
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, GRADIENT, x + 1, y, 0f, 0f, w - 2, h, 256, 16, 256, 16, col);
		g.blit(RenderPipelines.GUI_TEXTURED, GRADIENT, x, y + 1, 0f, 0f, 1, h - 2, 1, 16, 256, 16, col);
		g.blit(RenderPipelines.GUI_TEXTURED, GRADIENT, x + w - 1, y + 1, 255f, 0f, 1, h - 2, 1, 16, 256, 16, col);
	}

	/** Soft gradient glow around a box. */
	public static void glow(GuiGraphics g, int x, int y, int w, int h, float a) {
		if (a <= 0.01f) return;
		int p = Math.max(8, Math.min(24, h / 2 + 6));
		g.blit(RenderPipelines.GUI_TEXTURED, GLOW, x - p, y - p, 0f, 0f, w + 2 * p, h + 2 * p, 128, 64, 128, 64, white(a));
	}

	public static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
		g.fill(x + 1, y, x + w - 1, y + 1, c);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, c);
		g.fill(x, y + 1, x + 1, y + h - 1, c);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
	}

	/** Thin gradient underline / accent bar. */
	public static void gradientLine(GuiGraphics g, int x, int y, int w, int h, float a) {
		g.blit(RenderPipelines.GUI_TEXTURED, GRADIENT, x, y, 0f, 0f, w, h, 256, 16, 256, 16, white(a));
	}

	/** Logo image (1024x384) drawn at a given width. */
	public static void logo(GuiGraphics g, int x, int y, int w, float a) {
		int h = w * 384 / 1024;
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0f, 0f, w, h, 1024, 384, 1024, 384, white(a));
	}

	public static void vignette(GuiGraphics g, int w, int h, float a) {
		g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, w, h, 4, 256, 4, 256, white(a));
	}

	public static void text(GuiGraphics g, Font f, String s, int x, int y, int c) {
		g.drawString(f, s, x, y, c, false);
	}

	public static void textShadow(GuiGraphics g, Font f, String s, int x, int y, int c) {
		g.drawString(f, s, x, y, c, true);
	}

	public static void centered(GuiGraphics g, Font f, String s, int cx, int y, int c) {
		g.drawString(f, s, cx - f.width(s) / 2, y, c, false);
	}

	public static void scaled(GuiGraphics g, Font f, String s, float x, float y, float scale, int c, boolean shadow) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.drawString(f, s, 0, 0, c, shadow);
		g.pose().popMatrix();
	}

	public static String trim(Font f, String s, int maxW) {
		if (f.width(s) <= maxW) return s;
		return f.plainSubstrByWidth(s, Math.max(0, maxW - f.width("…"))) + "…";
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---------------------------------------------------------------- buttons

	/** A laG-styled button: dark glass, lights up with the gradient + glow on hover. */
	public static final class Btn {
		public int x, y, w, h;
		public String label;
		public final Runnable action;
		public boolean primary;
		private float hover;

		public Btn(String label, Runnable action) {
			this.label = label;
			this.action = action;
		}

		public Btn primary() {
			this.primary = true;
			return this;
		}

		public Btn at(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			return this;
		}

		public void render(GuiGraphics g, Font f, int mx, int my, float a) {
			boolean over = inside(mx, my, x, y, w, h);
			hover = approach(hover, over ? 1f : 0f, 14f);
			float lit = primary ? 0.55f + 0.45f * hover : hover;
			glow(g, x, y, w, h, a * (primary ? 0.35f + 0.45f * hover : 0.6f * hover));
			rect(g, x, y, w, h, alpha(CARD, a));
			gradient(g, x, y, w, h, a * lit);
			outline(g, x, y, w, h, alpha(lerp(0x30FFFFFF, 0x80FFFFFF, hover), a));
			int tc = alpha(lerp(primary ? TEXT : 0xFFC9D1DD, 0xFFFFFFFF, hover), a);
			g.drawString(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, tc, lit > 0.3f);
		}

		public boolean click(double mx, double my) {
			if (inside(mx, my, x, y, w, h)) {
				action.run();
				return true;
			}
			return false;
		}
	}

	/** Animated on/off switch. Stateless apart from its animation value. */
	public static final class Switch {
		private float t = -1;

		public void render(GuiGraphics g, int x, int y, boolean on, float a) {
			if (t < 0) t = on ? 1 : 0;
			t = approach(t, on ? 1f : 0f, 16f);
			int w = 22, h = 12;
			glow(g, x, y, w, h, a * 0.5f * t);
			rect(g, x, y, w, h, alpha(0xFF262E3B, a));
			gradient(g, x, y, w, h, a * t);
			int kx = x + 2 + Math.round(t * (w - 12));
			rect(g, kx, y + 2, 8, 8, alpha(0xFFFFFFFF, a));
		}
	}
}
