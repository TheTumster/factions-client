package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;

import com.factionsclient.ChatTabs;

/** Chat tabs bar while chat is open; clicking a tab; Faction/Ally/Private routing when you send. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Shadow
	protected EditBox input;

	@Inject(method = "render", at = @At("TAIL"))
	private void factionsclient$tabs(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		// Typing a command: vanilla shows suggestions and usage right where the tabs sit, so step aside
		if (input != null && input.getValue().startsWith("/")) {
			ChatTabs.hideBar();
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ChatTabs.renderBar(g, mc.font, g.guiHeight(), mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void factionsclient$clickTab(MouseButtonEvent e, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (e.button() == 0 && ChatTabs.click(e.x(), e.y())) cir.setReturnValue(true);
	}

	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void factionsclient$route(String msg, boolean addToRecent, CallbackInfo ci) {
		String m = msg.trim();
		if (m.isEmpty()) return;
		if (ChatTabs.onSend(m)) {
			if (addToRecent) Minecraft.getInstance().gui.getChat().addRecentChat(m);
			ci.cancel();
		}
	}
}
