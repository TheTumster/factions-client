package com.factionsclient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public class FactionsClient implements ClientModInitializer {
	public static final String MOD_ID = "factionsclient";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
	public static Config CONFIG;

	private static KeyMapping menuKey;
	private static KeyMapping clearKey;
	private static KeyMapping crumbsKey;

	@Override
	public void onInitializeClient() {
		CONFIG = Config.load();

		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
		clearKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.clear_crumbs", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, cat));
		crumbsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.toggle_crumbs", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, cat));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (menuKey.consumeClick()) mc.setScreen(new MenuScreen());
			while (clearKey.consumeClick()) {
				Crumbs.clear();
				SchemShare.msg("Crumbs cleared.", ChatFormatting.GRAY);
			}
			while (crumbsKey.consumeClick()) {
				CONFIG.crumbs = !CONFIG.crumbs;
				CONFIG.save();
				SchemShare.msg("Crumbs " + (CONFIG.crumbs ? "on" : "off"), ChatFormatting.GRAY);
			}
			Crumbs.tick(mc);
			TeamSync.tick(mc);
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), Hud::render);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldRender::render);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("ss")
					.then(literal("upload").then(argument("file", StringArgumentType.string())
							.suggests(SchemShare.FILES).executes(SchemShare::upload)))
					.then(literal("get").then(argument("code", StringArgumentType.word()).executes(SchemShare::get)))
					.then(literal("list").executes(SchemShare::list)));

			dispatcher.register(literal("fc")
					.executes(ctx -> help())
					.then(literal("help").executes(ctx -> help()))
					.then(literal("reload").executes(ctx -> {
						CONFIG = Config.load();
						TeamSync.lastError = null;
						SchemShare.msg("Config reloaded. Backend " + (CONFIG.backendReady() ? "ready." : "NOT set up."),
								CONFIG.backendReady() ? ChatFormatting.GREEN : ChatFormatting.RED);
						return 1;
					}))
					.then(literal("clear").executes(ctx -> {
						Crumbs.clear();
						return 1;
					})));
		});

		LOG.info("Factions Client loaded");
	}

	private static int help() {
		SchemShare.msg("Right Shift = menu, K = clear crumbs, J = toggle crumbs", ChatFormatting.WHITE);
		SchemShare.msg("/ss upload <file> | /ss get <code> | /ss list", ChatFormatting.WHITE);
		SchemShare.msg("/fc reload after editing config/factionsclient.json", ChatFormatting.WHITE);
		return 1;
	}
}
