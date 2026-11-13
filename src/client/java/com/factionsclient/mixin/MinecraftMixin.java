package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import com.factionsclient.FactionsClient;
import com.factionsclient.ui.LaGTitleScreen;

/** Whenever the game wants the vanilla main menu, show the laG one instead. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen factionsclient$swapTitle(Screen screen) {
		if (screen instanceof TitleScreen && FactionsClient.CONFIG != null && FactionsClient.CONFIG.customMenu) {
			return new LaGTitleScreen();
		}
		return screen;
	}
}
