package com.factionsclient.mixin;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.GuiMessageTag;

import com.factionsclient.ChatTabs;

/** Chat tabs: filter what's shown, timestamps, spam stacking, longer history. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	public abstract void rescaleChat();

	@Unique
	private boolean factionsclient$stacked;

	/** New message: timestamp it, or fold it into the previous identical line ("[x3]"). */
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component factionsclient$incoming(Component msg) {
		factionsclient$stacked = false;
		if (!ChatTabs.enabled()) return msg;
		boolean repeat = ChatTabs.stackRepeat(msg.getString());
		Component out = ChatTabs.incoming(msg);
		if (repeat && !allMessages.isEmpty()) {
			allMessages.removeFirst(); // the previous copy
			factionsclient$stacked = true;
			return ChatTabs.withCount(out);
		}
		return out;
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("TAIL"))
	private void factionsclient$afterAdd(Component msg, @Nullable MessageSignature sig, @Nullable GuiMessageTag tag, CallbackInfo ci) {
		if (factionsclient$stacked) {
			factionsclient$stacked = false;
			rescaleChat(); // drop the old copy's lines from the screen
		}
	}

	/** Lines outside the active tab don't show (they stay stored, so switching back brings them back). */
	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
	private void factionsclient$filter(GuiMessage msg, CallbackInfo ci) {
		if (!ChatTabs.visible(msg.content())) ci.cancel();
	}

	/** Keep more history so quiet tabs still have something to scroll back through. */
	@ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100))
	private int factionsclient$history(int vanilla) {
		return ChatTabs.enabled() ? 500 : vanilla;
	}
}
