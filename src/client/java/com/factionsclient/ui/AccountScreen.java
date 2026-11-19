package com.factionsclient.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import com.factionsclient.Accounts;
import com.factionsclient.FactionsClient;

/** Pick your Microsoft account or an offline ("cracked") name for offline-mode servers. */
public class AccountScreen extends Screen {
	private final Screen parent;
	private final List<Theme.Btn> buttons = new ArrayList<>();
	private EditBox nameBox;
	private String error;

	public AccountScreen(Screen parent) {
		super(Component.literal("Accounts"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		buttons.clear();
		int cx = this.width / 2, w = 260, x = cx - w / 2;
		int y = 64;
		String off = Accounts.activeOffline();

		Theme.Btn main = new Theme.Btn(Accounts.mainName(minecraft), () -> {
			Accounts.useMain(minecraft);
			rebuildWidgets();
		}).at(x, y, w, 24);
		main.sub = off == null ? "● in use  ·  Microsoft account" : "Microsoft account";
		main.subColor = off == null ? 0xFF6CFFB0 : 0xFFD8D0EA;
		if (off == null) main.primary();
		buttons.add(main);
		y += 30;

		for (String n : new ArrayList<>(FactionsClient.CONFIG.offlineAccounts)) {
			boolean active = n.equals(off);
			Theme.Btn b = new Theme.Btn(n, () -> {
				Accounts.useOffline(minecraft, n);
				rebuildWidgets();
			}).at(x, y, w - 30, 24);
			b.sub = active ? "● in use  ·  offline" : "offline";
			b.subColor = active ? 0xFF6CFFB0 : 0xFFD8D0EA;
			if (active) b.primary();
			buttons.add(b);
			buttons.add(new Theme.Btn("✕", () -> {
				Accounts.remove(minecraft, n);
				rebuildWidgets();
			}).at(x + w - 26, y, 26, 24));
			y += 30;
		}

		y += 6;
		nameBox = new EditBox(this.font, x, y, w - 86, 20, Component.literal("Offline name"));
		nameBox.setMaxLength(16);
		nameBox.setHint(Component.literal("Add an offline name..."));
		nameBox.setFilter(s -> s.matches("[A-Za-z0-9_]*"));
		addRenderableWidget(nameBox);
		buttons.add(new Theme.Btn("Add + use", this::add).at(x + w - 80, y - 1, 80, 22));
		y += 34;
		buttons.add(new Theme.Btn("Done", this::onClose).at(cx - 60, Math.max(y, this.height - 40), 120, 22));
	}

	private void add() {
		String n = nameBox.getValue().trim();
		if (!Accounts.VALID.matcher(n).matches()) {
			error = "Names are 3-16 letters, numbers or _";
			return;
		}
		error = null;
		Accounts.useOffline(minecraft, n);
		rebuildWidgets();
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		super.render(g, mx, my, delta);
		Theme.tickFrame();
		Theme.centered(g, this.font, "ACCOUNTS", this.width / 2, 24, Theme.TEXT);
		Theme.centered(g, this.font, "Offline names only work on offline-mode (cracked) servers.", this.width / 2, 40, Theme.MUTED);
		for (Theme.Btn b : buttons) b.render(g, this.font, mx, my, 1f);
		if (error != null) Theme.centered(g, this.font, error, this.width / 2, nameBox.getY() + 26, 0xFFFF6B7A);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.button() == 0) {
			for (Theme.Btn b : new ArrayList<>(buttons)) {
				if (b.click(e.x(), e.y())) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					return true;
				}
			}
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (nameBox != null && nameBox.isFocused() && (e.key() == 257 || e.key() == 335)) {
			add();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
