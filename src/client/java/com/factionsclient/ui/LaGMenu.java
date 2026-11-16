package com.factionsclient.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import com.factionsclient.FactionsClient;
import com.factionsclient.ModuleRegistry;
import com.factionsclient.ModuleRegistry.ActionS;
import com.factionsclient.ModuleRegistry.BoolS;
import com.factionsclient.ModuleRegistry.Category;
import com.factionsclient.ModuleRegistry.ColorS;
import com.factionsclient.ModuleRegistry.InfoS;
import com.factionsclient.ModuleRegistry.IntS;
import com.factionsclient.ModuleRegistry.KeyS;
import com.factionsclient.ModuleRegistry.Module;
import com.factionsclient.ModuleRegistry.Setting;
import com.factionsclient.ModuleRegistry.TextS;

/** The Right Shift menu: categories on the left, module cards, and a settings page for every module. */
public class LaGMenu extends Screen {
	public static final int[] PALETTE = {0xFF38E0FF, 0xFF4F8BFF, 0xFFB040FF, 0xFFFF60C0, 0xFFFF3B3B,
			0xFFFF7A1F, 0xFFFFD23F, 0xFF8CFF40, 0xFF20C060, 0xFFFFFFFF};

	private record Hit(int x, int y, int w, int h, Runnable action) { }

	private final Screen parent;
	private static Category category = Category.MODES;
	private Module open = null;
	private List<Setting> openSettings = List.of();

	private float openAnim = 0f;
	private float scroll = 0f, scrollTarget = 0f, maxScroll = 0f;
	private final Map<Object, Theme.Switch> switches = new HashMap<>();
	private final Map<Object, Float> hovers = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();

	private String search = "";
	private boolean searchFocused = false;
	private TextS focusedText = null;
	private KeyS listening = null;
	private IntS dragging = null;
	private int dragX, dragW;

	private int wx, wy, ww, wh;
	private boolean rightClick = false;
	/** When the visible card list last changed (tab switch / search): cards slide in one after another. */
	private long listShownAt = System.currentTimeMillis();

	public LaGMenu(Screen parent) {
		super(Component.literal(Theme.BRAND));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ModuleRegistry.init();
		ModuleRegistry.parentScreen = this;
		ww = Math.min(640, this.width - 24);
		wh = Math.min(360, this.height - 24);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
	}

	/** Jump straight to a module's settings page (used by the smoke test). */
	public void openPage(String moduleName) {
		for (Module m : ModuleRegistry.ALL) {
			if (m.name.equals(moduleName)) {
				category = m.category;
				open = m;
				openSettings = m.settings.get();
				scroll = scrollTarget = 0;
				return;
			}
		}
	}

	// ================================================================ render

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		Theme.tickFrame();
		super.render(g, mx, my, delta);
		hits.clear();
		openAnim = Theme.approach(openAnim, 1f, 12f);
		float a = openAnim;
		float s = 0.94f + 0.06f * a;

		g.pose().pushMatrix();
		g.pose().translate(this.width / 2f, this.height / 2f);
		g.pose().scale(s, s);
		g.pose().translate(-this.width / 2f, -this.height / 2f);
		// mouse in panel space
		int pmx = Math.round((mx - this.width / 2f) / s + this.width / 2f);
		int pmy = Math.round((my - this.height / 2f) / s + this.height / 2f);

		// Window
		Theme.glow(g, wx, wy, ww, wh, 0.35f * a);
		Theme.rect(g, wx, wy, ww, wh, Theme.alpha(Theme.PANEL, a));
		Theme.rect(g, wx, wy, 120, wh, Theme.alpha(Theme.SIDEBAR, a));
		g.fill(wx + 120, wy + 2, wx + 121, wy + wh - 1, Theme.alpha(Theme.LINE, a));
		g.fillGradient(wx + 121, wy + 2, wx + ww - 1, wy + 40, Theme.alpha(0x14B040FF, a), 0x00000000);
		Theme.gradientLine(g, wx, wy, ww, 2, a);
		Theme.outline(g, wx, wy, ww, wh, Theme.alpha(Theme.LINE, a));

		sidebar(g, pmx, pmy, a);
		if (open == null) cards(g, pmx, pmy, a);
		else settingsPage(g, pmx, pmy, a);

		g.pose().popMatrix();
	}

	private void sidebar(GuiGraphics g, int mx, int my, float a) {
		Theme.logo(g, wx + 10, wy + 8, 100, a);
		int y = wy + 50;
		int step = Math.max(19, Math.min(23, (wh - 50 - 22) / Category.values().length));
		for (Category c : Category.values()) {
			boolean sel = c == category && search.isEmpty();
			float h = hover(c, Theme.inside(mx, my, wx + 8, y, 104, 20));
			if (sel) {
				Theme.glow(g, wx + 8, y, 104, 20, 0.45f * a);
				Theme.gradient(g, wx + 8, y, 104, 20, a);
			} else if (h > 0.01f) {
				Theme.rect(g, wx + 8, y, 104, 20, Theme.alpha(0x20FFFFFF, a * h));
			}
			int tc = sel ? 0xFFFFFFFF : Theme.lerp(Theme.MUTED, Theme.TEXT, h);
			g.drawString(font, c.icon, wx + 16, y + 6, Theme.alpha(tc, a), sel);
			g.drawString(font, c.label, wx + 30, y + 6, Theme.alpha(tc, a), sel);
			final Category cc = c;
			hits.add(new Hit(wx + 8, y, 104, 20, () -> {
				if (category != cc) listShownAt = System.currentTimeMillis();
				category = cc;
				open = null;
				search = "";
				searchFocused = false;
				scroll = scrollTarget = 0;
			}));
			y += step;
		}
		// Current mode, bottom of the sidebar (click: go to Modes)
		int py = wy + wh - 26;
		if (y + 4 < py) {
			var mode = com.factionsclient.Modes.current();
			String label = mode.icon + " " + mode.label + " mode";
			float ph = hover("modepill", Theme.inside(mx, my, wx + 8, py, 104, 18));
			Theme.rect(g, wx + 8, py, 104, 18, Theme.alpha(Theme.lerp(Theme.CARD, Theme.CARD_HOVER, ph), a));
			Theme.gradient(g, wx + 8, py + 17, 104, 1, a * 0.9f);
			Theme.outline(g, wx + 8, py, 104, 18, Theme.alpha(0x30FFFFFF, a));
			g.drawString(font, Theme.trim(font, label, 96), wx + 13, py + 5, Theme.alpha(Theme.TEXT, a), false);
			hits.add(new Hit(wx + 8, py, 104, 18, () -> {
				if (category != Category.MODES) listShownAt = System.currentTimeMillis();
				category = Category.MODES;
				open = null;
				search = "";
				scroll = scrollTarget = 0;
			}));
		}
	}

	private void cards(GuiGraphics g, int mx, int my, float a) {
		int cx = wx + 132, cw = ww - 132 - 12;
		Theme.scaled(g, font, search.isEmpty() ? category.label : "Search", cx, wy + 12, 1.4f, Theme.alpha(Theme.TEXT, a), false);

		// search box
		int sw = 130, sx = wx + ww - 12 - sw, sy = wy + 10;
		Theme.rect(g, sx, sy, sw, 16, Theme.alpha(Theme.FIELD, a));
		Theme.outline(g, sx, sy, sw, 16, Theme.alpha(searchFocused ? 0x90B040FF : Theme.LINE, a));
		String st = search.isEmpty() && !searchFocused ? "⌕ Search modules" : search + (searchFocused && blink() ? "_" : "");
		g.drawString(font, Theme.trim(font, st, sw - 8), sx + 5, sy + 4, Theme.alpha(search.isEmpty() && !searchFocused ? Theme.DIM : Theme.TEXT, a), false);
		hits.add(new Hit(sx, sy, sw, 16, () -> {
			searchFocused = true;
			focusedText = null;
		}));

		List<Module> list = new ArrayList<>();
		String q = search.toLowerCase(Locale.ROOT);
		for (Module m : ModuleRegistry.ALL) {
			if (q.isEmpty() ? m.category == category : (m.name + " " + m.desc).toLowerCase(Locale.ROOT).contains(q)) list.add(m);
		}

		int top = wy + 36, bottom = wy + wh - 10;
		int gap = 8;
		int cols = Math.max(1, Math.min(2, (cw + gap) / (200 + gap)));
		int colW = (cw - (cols - 1) * gap) / cols, cardH = 50;
		int rows = (list.size() + cols - 1) / cols;
		maxScroll = Math.max(0, rows * (cardH + gap) - gap - (bottom - top));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Theme.approach(scroll, scrollTarget, 16f);

		g.enableScissor(cx - 4, top - 2, cx + cw + 4, bottom);
		long now = System.currentTimeMillis();
		for (int i = 0; i < list.size(); i++) {
			Module m = list.get(i);
			int x = cx + (i % cols) * (colW + gap);
			int y = top + (i / cols) * (cardH + gap) - Math.round(scroll);
			if (y + cardH < top || y > bottom) continue;
			float t = Math.max(0f, Math.min(1f, (now - listShownAt - Math.min(i, 10) * 28) / 260f));
			float e = 1f - (1f - t) * (1f - t) * (1f - t);
			int slide = Math.round((1 - e) * 8);
			card(g, m, x, y + slide, colW, cardH, mx, my, a * e, my >= top && my < bottom && e > 0.9f);
		}
		g.disableScissor();
		if (list.isEmpty()) g.drawString(font, "Nothing matches \"" + search + "\"", cx, top + 4, Theme.alpha(Theme.MUTED, a), false);
	}

	private void card(GuiGraphics g, Module m, int x, int y, int w, int h, int mx, int my, float a, boolean inView) {
		boolean over = inView && Theme.inside(mx, my, x, y, w, h);
		float hv = hover(m, over);
		boolean on = m.toggle != null && m.toggle.get.get();

		Theme.glow(g, x, y, w, h, a * (0.12f * hv + (on ? 0.18f : 0f)));
		Theme.rect(g, x, y, w, h, Theme.alpha(Theme.lerp(Theme.CARD, Theme.CARD_HOVER, hv), a));
		if (on) Theme.gradient(g, x, y, 3, h, a);
		Theme.outline(g, x, y, w, h, Theme.alpha(Theme.lerp(Theme.LINE, 0x50FFFFFF, hv), a));

		boolean hasPage = m.open != null || m.hasSettings();
		int nameRoom = w - 20 - (m.toggle != null ? 36 : 0) - (m.toggle == null && hasPage ? 14 : 0);
		g.drawString(font, Theme.trim(font, m.name, nameRoom), x + 10, y + 9, Theme.alpha(on ? 0xFFFFFFFF : Theme.TEXT, a), false);
		// Description wraps onto two lines (scaled down a touch) instead of being cut off
		int descRoom = w - 20 - (m.toggle != null && hasPage ? 16 : 0);
		var lines = font.split(Component.literal(m.desc), Math.round(descRoom / 0.85f));
		for (int li = 0; li < Math.min(2, lines.size()); li++) {
			g.pose().pushMatrix();
			g.pose().translate(x + 10, y + 24 + li * 10);
			g.pose().scale(0.85f, 0.85f);
			var seq = lines.get(li);
			if (li == 1 && lines.size() > 2) {
				String rest = Theme.trim(font, m.desc.substring(Math.min(m.desc.length(), Math.max(0, cutIndex(m.desc, lines.get(0))))), Math.round(descRoom / 0.85f));
				g.drawString(font, rest, 0, 0, Theme.alpha(Theme.MUTED, a), false);
			} else {
				g.drawString(font, seq, 0, 0, Theme.alpha(Theme.MUTED, a), false);
			}
			g.pose().popMatrix();
		}
		if (m.toggle != null) {
			int sx = x + w - 32, sy = y + 8;
			sw(m).render(g, sx, sy, on, a);
			if (inView) hits.add(new Hit(sx - 4, sy - 4, 30, 20, () -> {
				m.toggle.set.accept(!m.toggle.get.get());
				FactionsClient.CONFIG.save();
				click();
			}));
		} else if (hasPage) {
			g.drawString(font, "›", x + w - 16, y + 9, Theme.alpha(Theme.lerp(Theme.MUTED, 0xFFFFFFFF, hv), a), false);
		}
		if (m.toggle != null && hasPage && hv > 0.05f) {
			// small gear under the switch: this card has settings
			g.drawString(font, "⚙", x + w - 24, y + 26, Theme.alpha(Theme.lerp(Theme.MUTED, 0xFFFFFFFF, hv), a * hv), false);
		}

		if (inView) hits.add(new Hit(x, y, w, h, () -> {
			click();
			if (m.open != null) {
				m.open.run();
			} else if (hasPage) {
				open = m;
				openSettings = m.settings.get();
				scroll = scrollTarget = 0;
			} else if (m.toggle != null) {
				m.toggle.set.accept(!m.toggle.get.get());
				FactionsClient.CONFIG.save();
			}
		}));
	}

	/** How many characters of `full` the first wrapped line used. */
	private int cutIndex(String full, net.minecraft.util.FormattedCharSequence first) {
		StringBuilder sb = new StringBuilder();
		first.accept((idx, style, cp) -> {
			sb.appendCodePoint(cp);
			return true;
		});
		int n = sb.length();
		while (n < full.length() && full.charAt(n) == ' ') n++;
		return n;
	}

	private void settingsPage(GuiGraphics g, int mx, int my, float a) {
		int cx = wx + 132, cw = ww - 132 - 12;
		float hb = hover("back", Theme.inside(mx, my, cx, wy + 10, 18, 18));
		Theme.rect(g, cx, wy + 10, 18, 18, Theme.alpha(Theme.lerp(Theme.CARD, Theme.CARD_HOVER, hb), a));
		g.drawString(font, "‹", cx + 7, wy + 15, Theme.alpha(Theme.TEXT, a), false);
		hits.add(new Hit(cx, wy + 10, 18, 18, () -> {
			open = null;
			focusedText = null;
			listening = null;
			scroll = scrollTarget = 0;
		}));
		Theme.scaled(g, font, open.name, cx + 26, wy + 12, 1.4f, Theme.alpha(Theme.TEXT, a), false);
		g.drawString(font, Theme.trim(font, open.desc, cw - 30), cx + 26, wy + 26, Theme.alpha(Theme.MUTED, a), false);

		List<Setting> rows = new ArrayList<>();
		if (open.toggle != null) rows.add(new BoolS("Enabled", open.toggle.get, open.toggle.set));
		rows.addAll(openSettings);

		int top = wy + 44, bottom = wy + wh - 10, rowH = 26;
		maxScroll = Math.max(0, rows.size() * rowH - (bottom - top));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll = Theme.approach(scroll, scrollTarget, 16f);

		g.enableScissor(cx - 4, top - 2, cx + cw + 4, bottom);
		for (int i = 0; i < rows.size(); i++) {
			int y = top + i * rowH - Math.round(scroll);
			if (y + rowH < top || y > bottom) continue;
			row(g, rows.get(i), cx, y, cw, rowH - 4, mx, my, a, my >= top && my < bottom);
		}
		g.disableScissor();
	}

	private void row(GuiGraphics g, Setting s, int x, int y, int w, int h, int mx, int my, float a, boolean inView) {
		Theme.rect(g, x, y, w, h, Theme.alpha(Theme.CARD, a));
		int midY = y + (h - 8) / 2;
		int right = x + w - 8;

		if (s instanceof InfoS info) {
			g.drawString(font, Theme.trim(font, info.text.get(), w - 16), x + 8, midY, Theme.alpha(Theme.MUTED, a), false);
			return;
		}
		g.drawString(font, Theme.trim(font, s.name, w / 2), x + 8, midY, Theme.alpha(Theme.TEXT, a), false);

		if (s instanceof BoolS b) {
			boolean on = b.get.get();
			sw(b).render(g, right - 22, y + (h - 12) / 2, on, a);
			if (inView) hits.add(new Hit(x, y, w, h, () -> {
				b.set.accept(!b.get.get());
				FactionsClient.CONFIG.save();
				click();
			}));
		} else if (s instanceof IntS is) {
			int sw = Math.min(150, w / 2 - 50), sx = right - sw, ty = y + h / 2 - 2;
			int v = is.get.get();
			float t = (v - is.min) / (float) Math.max(1, is.max - is.min);
			String val = is.show(v);
			g.drawString(font, val, sx - 8 - font.width(val), midY, Theme.alpha(Theme.MUTED, a), false);
			Theme.rect(g, sx, ty, sw, 4, Theme.alpha(0xFF262E3B, a));
			Theme.gradient(g, sx, ty, Math.max(2, Math.round(sw * t)), 4, a);
			int kx = sx + Math.round(sw * t) - 4;
			boolean active = dragging == is;
			Theme.glow(g, kx, ty - 2, 8, 8, a * (active ? 0.7f : 0.3f));
			Theme.rect(g, kx, ty - 2, 8, 8, Theme.alpha(0xFFFFFFFF, a));
			if (inView) hits.add(new Hit(sx - 4, y, sw + 8, h, () -> {
				dragging = is;
				dragX = sx;
				dragW = sw;
			}));
		} else if (s instanceof ColorS cs) {
			int cur = cs.get.get();
			int size = 10, gap = 4;
			int sx = right - PALETTE.length * (size + gap) + gap;
			for (int i = 0; i < PALETTE.length; i++) {
				int px = sx + i * (size + gap), py = y + (h - size) / 2;
				int col = PALETTE[i];
				boolean sel = (col & 0xFFFFFF) == (cur & 0xFFFFFF);
				if (sel) {
					Theme.glow(g, px, py, size, size, 0.6f * a);
					Theme.outline(g, px - 2, py - 2, size + 4, size + 4, Theme.alpha(0xFFFFFFFF, a));
				}
				Theme.rect(g, px, py, size, size, Theme.alpha(col, a));
				if (inView) hits.add(new Hit(px - 1, py - 1, size + 2, size + 2, () -> {
					cs.set.accept(col);
					FactionsClient.CONFIG.save();
					click();
				}));
			}
		} else if (s instanceof KeyS ks) {
			KeyMapping km = ks.key.get();
			boolean lis = listening == ks;
			String label = lis ? "Press a key…"
					: ks.label != null ? (ks.label.get() == null || ks.label.get().isBlank() ? "None" : ks.label.get())
					: km == null ? "?" : km.isUnbound() ? "None" : km.getTranslatedKeyMessage().getString();
			int bw = Math.max(70, font.width(label) + 16), bx = right - bw, by = y + (h - 16) / 2;
			float hv = hover(ks, Theme.inside(mx, my, bx, by, bw, 16));
			if (lis) {
				float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 150.0);
				Theme.glow(g, bx, by, bw, 16, 0.5f * pulse * a);
				Theme.gradient(g, bx, by, bw, 16, (0.6f + 0.4f * pulse) * a);
			} else {
				Theme.rect(g, bx, by, bw, 16, Theme.alpha(Theme.lerp(Theme.FIELD, Theme.CARD_HOVER, hv), a));
				Theme.outline(g, bx, by, bw, 16, Theme.alpha(Theme.LINE, a));
			}
			Theme.centered(g, font, label, bx + bw / 2, by + 4, Theme.alpha(0xFFFFFFFF, a));
			if (inView) hits.add(new Hit(bx, by, bw, 16, () -> {
				listening = ks;
				focusedText = null;
				searchFocused = false;
				click();
			}));
		} else if (s instanceof ModuleRegistry.ChoiceS ch) {
			String label = ch.value.get() + "  ›";
			int bw = Math.max(70, font.width(label) + 18), bx = right - bw, by = y + (h - 16) / 2;
			float hv = hover(ch, Theme.inside(mx, my, bx, by, bw, 16));
			Theme.rect(g, bx, by, bw, 16, Theme.alpha(Theme.lerp(Theme.FIELD, Theme.CARD_HOVER, hv), a));
			Theme.gradient(g, bx, by + 15, bw, 1, a * (0.5f + 0.5f * hv));
			Theme.outline(g, bx, by, bw, 16, Theme.alpha(Theme.LINE, a));
			Theme.centered(g, font, label, bx + bw / 2, by + 4, Theme.alpha(0xFFFFFFFF, a));
			if (inView) hits.add(new Hit(bx, by, bw, 16, () -> {
				click();
				ch.cycle.accept(!rightClick);
				FactionsClient.CONFIG.save();
			}));
		} else if (s instanceof ActionS act) {
			int bw = font.width(act.button) + 18, bx = right - bw, by = y + (h - 16) / 2;
			Theme.Btn b = btn(act);
			b.label = act.button;
			b.at(bx, by, bw, 16).render(g, font, mx, my, a);
			if (inView) hits.add(new Hit(bx, by, bw, 16, () -> {
				click();
				act.run.run();
				FactionsClient.CONFIG.save();
				if (open != null) openSettings = open.settings.get(); // refresh (e.g. file list)
			}));
		} else if (s instanceof TextS ts) {
			int bw = font.width(ts.button) + 18, bx = right - bw, by = y + (h - 16) / 2;
			int fw = 90, fx = bx - 6 - fw;
			boolean foc = focusedText == ts;
			Theme.rect(g, fx, by, fw, 16, Theme.alpha(Theme.FIELD, a));
			Theme.outline(g, fx, by, fw, 16, Theme.alpha(foc ? 0x90B040FF : Theme.LINE, a));
			String shown = ts.value.isEmpty() && !foc ? ts.hint : ts.value + (foc && blink() ? "_" : "");
			g.drawString(font, shown, fx + 5, by + 4, Theme.alpha(ts.value.isEmpty() && !foc ? Theme.DIM : Theme.TEXT, a), false);
			if (inView) hits.add(new Hit(fx, by, fw, 16, () -> {
				focusedText = ts;
				searchFocused = false;
				listening = null;
			}));
			Theme.Btn b = btn(ts);
			b.label = ts.button;
			b.primary = true;
			b.at(bx, by, bw, 16).render(g, font, mx, my, a);
			if (inView) hits.add(new Hit(bx, by, bw, 16, () -> {
				click();
				ts.submit.accept(ts.value);
				ts.value = "";
				focusedText = null;
			}));
		}
	}

	// ================================================================ helpers

	private final Map<Object, Theme.Btn> btns = new HashMap<>();

	private Theme.Btn btn(Object key) {
		return btns.computeIfAbsent(key, k -> new Theme.Btn("", () -> { }));
	}

	private Theme.Switch sw(Object key) {
		return switches.computeIfAbsent(key, k -> new Theme.Switch());
	}

	private float hover(Object key, boolean over) {
		float v = Theme.approach(hovers.getOrDefault(key, 0f), over ? 1f : 0f, 14f);
		hovers.put(key, v);
		return v;
	}

	private static boolean blink() {
		return (System.currentTimeMillis() / 500) % 2 == 0;
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private float s() {
		return 0.94f + 0.06f * openAnim;
	}

	private double px(double mx) {
		return (mx - this.width / 2f) / s() + this.width / 2f;
	}

	private double py(double my) {
		return (my - this.height / 2f) / s() + this.height / 2f;
	}

	// ================================================================ input

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.button() != 0 && e.button() != 1) return super.mouseClicked(e, doubleClick);
		rightClick = e.button() == 1; // right click steps choices backwards
		double mx = px(e.x()), my = py(e.y());
		boolean hadFocus = searchFocused || focusedText != null;
		listening = null;
		// Last registered = drawn on top, so check in reverse.
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Theme.inside(mx, my, h.x, h.y, h.w, h.h)) {
				if (hadFocus) {
					searchFocused = false;
					focusedText = null;
				}
				h.action.run();
				if (dragging != null) updateDrag(mx);
				return true;
			}
		}
		searchFocused = false;
		focusedText = null;
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging != null) {
			updateDrag(px(e.x()));
			return true;
		}
		return super.mouseDragged(e, dx, dy);
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

	private void updateDrag(double mx) {
		IntS s = dragging;
		float t = (float) Math.max(0, Math.min(1, (mx - dragX) / Math.max(1, dragW)));
		int raw = s.min + Math.round(t * (s.max - s.min));
		int v = s.min + Math.round((raw - s.min) / (float) s.step) * s.step;
		s.set.accept(Math.max(s.min, Math.min(s.max, v)));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - v * 24));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		int key = e.key();
		if (listening != null) {
			if (listening.bind != null) {
				if (key != InputConstants.KEY_ESCAPE) listening.bind.accept(key == InputConstants.KEY_BACKSPACE ? -1 : key);
				listening = null;
				return true;
			}
			KeyMapping km = listening.key.get();
			if (km != null && key != InputConstants.KEY_ESCAPE) {
				km.setKey(key == InputConstants.KEY_BACKSPACE ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(key));
				KeyMapping.resetMapping();
				minecraft.options.save();
			}
			listening = null;
			return true;
		}
		if (focusedText != null || searchFocused) {
			if (key == InputConstants.KEY_ESCAPE) {
				focusedText = null;
				searchFocused = false;
			} else if (key == InputConstants.KEY_BACKSPACE) {
				if (focusedText != null && !focusedText.value.isEmpty()) focusedText.value = focusedText.value.substring(0, focusedText.value.length() - 1);
				if (searchFocused && !search.isEmpty()) search = search.substring(0, search.length() - 1);
			} else if (key == InputConstants.KEY_RETURN && focusedText != null) {
				focusedText.submit.accept(focusedText.value);
				focusedText.value = "";
				focusedText = null;
			}
			return true;
		}
		if (FactionsClient.menuKey != null && FactionsClient.menuKey.matches(e)) {
			onClose();
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE && open != null) {
			open = null;
			scroll = scrollTarget = 0;
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		String ch = new String(Character.toChars(e.codepoint()));
		if (focusedText != null) {
			if (focusedText.value.length() < focusedText.maxLen) focusedText.value += ch;
			return true;
		}
		if (searchFocused) {
			if (search.length() < 30) search += ch;
			open = null;
			scroll = scrollTarget = 0;
			return true;
		}
		return super.charTyped(e);
	}

	@Override
	public void onClose() {
		FactionsClient.CONFIG.save();
		ModuleRegistry.parentScreen = null;
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
