package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import com.factionsclient.FactionsClient;
import com.factionsclient.ui.LaGTitleScreen;

/** Whenever the game wants the vanilla main menu, show ours instead. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen factionsclient$swapTitle(Screen screen) {
		if (screen instanceof TitleScreen && FactionsClient.CONFIG != null && FactionsClient.CONFIG.customMenu) {
			return new LaGTitleScreen();
		}
		return screen;
	}

	/** Window title: "RelapsedMC Client" instead of "Minecraft 1.21.11 - Multiplayer (3rd-party Server)". */
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void factionsclient$title(CallbackInfoReturnable<String> cir) {
		Minecraft mc = (Minecraft) (Object) this;
		String where = mc.getCurrentServer() != null && com.factionsclient.RelapsedServer.isRelapsed(mc.getCurrentServer().ip)
				? "  -  Playing RelapsedMC" : "";
		cir.setReturnValue("RelapsedMC Client" + where);
	}
}
