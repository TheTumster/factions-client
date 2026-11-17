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
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public class FactionsClient implements ClientModInitializer {
	public static final String MOD_ID = "factionsclient";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
	public static Config CONFIG;

	public static KeyMapping menuKey;
	public static KeyMapping clearKey;
	public static KeyMapping crumbsKey;
	public static KeyMapping zoomKey;
	public static KeyMapping chunkKey;
	public static KeyMapping fullbrightKey;
	public static KeyMapping pingKey;

	@Override
	public void onInitializeClient() {
		CONFIG = Config.load();
		ServerLink.init();
		TeamLink.init();

		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
		clearKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.clear_crumbs", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, cat));
		crumbsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.toggle_crumbs", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, cat));

		zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, cat));
		chunkKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.chunk_borders", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, cat));
		fullbrightKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.fullbright", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
		pingKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.factionsclient.ping", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, cat));

		ClientLifecycleEvents.CLIENT_STARTED.register(FactionsClient::moveXaeroMapKey);
		ClientLifecycleEvents.CLIENT_STARTED.register(Performance::tuneOnce);
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> Updater.checkAsync());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> Updater.announce());
		ClientLifecycleEvents.CLIENT_STOPPING.register(Modules::restoreAll);
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> DiscordPresence.close());

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Modules.tick(mc, zoomKey);
			if (Smoke.enabled()) Smoke.tick(mc);
			if (HubPreview.enabled()) HubPreview.tick(mc);
			DiscordPresence.tick(mc);
			while (menuKey.consumeClick()) mc.setScreen(new com.factionsclient.ui.LaGMenu(mc.screen));
			while (chunkKey.consumeClick()) {
				CONFIG.chunkGrid = !CONFIG.chunkGrid;
				CONFIG.save();
				SchemShare.msg("Chunk borders " + (CONFIG.chunkGrid ? "on" : "off"), ChatFormatting.GRAY);
			}
			while (fullbrightKey.consumeClick()) {
				CONFIG.fullbright = !CONFIG.fullbright;
				CONFIG.save();
				SchemShare.msg("Fullbright " + (CONFIG.fullbright ? "on" : "off"), ChatFormatting.GRAY);
			}
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
			ServerLink.tick(mc);
			TeamLink.tick(mc);
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
						SchemShare.msg("Config reloaded.", ChatFormatting.GREEN);
						return 1;
					}))
					.then(literal("clear").executes(ctx -> {
						Crumbs.clear();
						return 1;
					})));
		});

		LOG.info("laG Client loaded"); // smoke test greps this exact line
	}

	/** Xaero's world map and Litematica both default to M. Move the world map to N, once. */
	private static void moveXaeroMapKey(Minecraft mc) {
		if (CONFIG.xaeroKeyFixed) return;
		boolean changed = false;
		for (KeyMapping k : mc.options.keyMappings) {
			String name = k.getName().toLowerCase();
			if (name.contains("xaero") && name.contains("open_map") && k.saveString().equals("key.keyboard.m")) {
				k.setKey(InputConstants.getKey("key.keyboard.n"));
				changed = true;
			}
		}
		if (changed) {
			KeyMapping.resetMapping();
			mc.options.save();
			LOG.info("Moved Xaero's world map key from M to N");
		}
		CONFIG.xaeroKeyFixed = true;
		CONFIG.save();
	}

	private static int help() {
		SchemShare.msg("Right Shift = client menu, G = chunk borders, C = zoom, J/K = crumbs", ChatFormatting.WHITE);
		SchemShare.msg("/ss upload <file> | /ss get <code> | /ss list", ChatFormatting.WHITE);
		SchemShare.msg("/fc reload after editing config/factionsclient.json", ChatFormatting.WHITE);
		return 1;
	}
}
