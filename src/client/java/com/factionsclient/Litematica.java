package com.factionsclient;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Bridge to Litematica + Litematica Printer without compiling against them
 * (so our mod still loads if they're missing or update).
 */
public final class Litematica {
	private static final String[] PRINTER_CLASSES = {
			"me.aleksilassila.litematica.printer.config.Configs", // 3.x (the version the pack ships)
			"me.aleksilassila.litematica.printer.common.LitematicaMod",
			"me.aleksilassila.litematica.printer.LitematicaMixinMod",
			"me.aleksilassila.litematica.printer.LitematicaMod",
	};

	private Litematica() { }

	public static boolean present() {
		return FabricLoader.getInstance().isModLoaded("litematica");
	}

	/** Opens a Litematica screen by class name (e.g. "GuiMainMenu"), returning to `parent` on Esc. */
	public static void open(String guiClass, Screen parent) {
		try {
			Class<?> c = Class.forName("fi.dy.masa.litematica.gui." + guiClass);
			Screen s = (Screen) c.getConstructor().newInstance();
			try {
				c.getMethod("setParent", Screen.class).invoke(s, parent);
			} catch (Exception ignored) {
				// older malilib: no parent support
			}
			Minecraft.getInstance().setScreen(s);
		} catch (Throwable t) {
			FactionsClient.LOG.warn("Couldn't open Litematica {}: {}", guiClass, t.toString());
			SchemShare.msg("Couldn't open that Litematica screen (is Litematica installed?)", net.minecraft.ChatFormatting.RED);
		}
	}

	// ---- malilib boolean configs via reflection ----

	private static Object field(String className, String fieldName) {
		try {
			Field f = Class.forName(className).getField(fieldName);
			return f.get(null);
		} catch (Throwable t) {
			return null;
		}
	}

	private static Boolean getBool(Object cfg) {
		if (cfg == null) return null;
		try {
			return (Boolean) cfg.getClass().getMethod("getBooleanValue").invoke(cfg);
		} catch (Throwable t) {
			return null;
		}
	}

	private static void setBool(Object cfg, boolean v) {
		if (cfg == null) return;
		try {
			cfg.getClass().getMethod("setBooleanValue", boolean.class).invoke(cfg, v);
		} catch (Throwable t) {
			FactionsClient.LOG.warn("Couldn't set Litematica option: {}", t.toString());
		}
	}

	private static Object printerMode() {
		for (String c : PRINTER_CLASSES) {
			Object o = field(c, "PRINT_MODE");
			if (o != null) return o;
		}
		return null;
	}

	private static Object rendering() {
		return field("fi.dy.masa.litematica.config.Configs$Visuals", "ENABLE_RENDERING");
	}

	private static Object easyPlace() {
		return field("fi.dy.masa.litematica.config.Configs$Generic", "EASY_PLACE_MODE");
	}

	/** null = printer not installed */
	public static Boolean printer() {
		return getBool(printerMode());
	}

	public static void setPrinter(boolean v) {
		setBool(printerMode(), v);
	}

	public static Boolean schematicRendering() {
		return getBool(rendering());
	}

	public static void setSchematicRendering(boolean v) {
		setBool(rendering(), v);
	}

	public static Boolean easyPlaceMode() {
		return getBool(easyPlace());
	}

	public static void setEasyPlaceMode(boolean v) {
		setBool(easyPlace(), v);
	}
}
