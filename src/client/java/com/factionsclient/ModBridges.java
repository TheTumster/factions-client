package com.factionsclient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;

import com.factionsclient.ModuleRegistry.BoolS;
import com.factionsclient.ModuleRegistry.ChoiceS;
import com.factionsclient.ModuleRegistry.ColorS;
import com.factionsclient.ModuleRegistry.InfoS;
import com.factionsclient.ModuleRegistry.IntS;
import com.factionsclient.ModuleRegistry.KeyS;
import com.factionsclient.ModuleRegistry.Setting;
import com.factionsclient.ModuleRegistry.TextS;

/**
 * Puts other mods' settings inside our menu, so players never land in a different-looking config screen.
 * Everything is reflection: if a mod is missing or changes, its rows just don't appear.
 *
 *  - malilib (Litematica + Litematica Printer): booleans, numbers, choices, colors, text, hotkeys
 *  - Xaero's Minimap (xaero.lib config): every minimap option, saved to the player's current profile
 */
public final class ModBridges {
	private ModBridges() { }

	// ================================================================ malilib

	/** Litematica owns the printer's options too (the printer mixes them into Litematica's config file). */
	public static void saveLitematica() {
		try {
			Class.forName("fi.dy.masa.litematica.config.Configs").getMethod("saveToFile").invoke(null);
		} catch (Throwable t) {
			FactionsClient.LOG.warn("Couldn't save Litematica config: {}", t.toString());
		}
	}

	/** A static list from a class, e.g. ("...printer.config.Configs", "getConfigList") or a field like OPTIONS. */
	public static List<?> malilibList(String className, String member) {
		try {
			Class<?> c = Class.forName(className);
			Object o;
			try {
				o = c.getMethod(member).invoke(null);
			} catch (NoSuchMethodException e) {
				o = c.getField(member).get(null);
			}
			return o instanceof List<?> l ? l : List.of();
		} catch (Throwable t) {
			return List.of();
		}
	}

	/** The malilib options declared as public static fields of one class (not lists other mods mixed into). */
	public static List<?> malilibFields(String className, String... skip) {
		List<Object> out = new ArrayList<>();
		java.util.Set<String> skipSet = java.util.Set.of(skip);
		try {
			for (Field f : Class.forName(className).getFields()) {
				if (!Modifier.isStatic(f.getModifiers()) || skipSet.contains(f.getName())) continue;
				Object o = f.get(null);
				if (o != null && o.getClass().getName().startsWith("fi.dy.masa.malilib.config.options.")) out.add(o);
			}
		} catch (Throwable ignored) { }
		return out;
	}

	public static List<Setting> malilib(List<?> configs, Runnable save) {
		List<Setting> out = new ArrayList<>();
		for (Object cfg : configs) {
			try {
				Setting s = malilibOne(cfg, save);
				if (s != null) out.add(s);
			} catch (Throwable ignored) {
				// one odd option shouldn't hide the rest
			}
		}
		return out;
	}

	private static Setting malilibOne(Object cfg, Runnable save) throws Exception {
		String name = str(call(cfg, "getPrettyName"));
		if (name == null || name.isBlank()) name = pretty(str(call(cfg, "getName")));
		String cls = cfg.getClass().getSimpleName();

		if (has(cfg, "getKeybind") && !has(cfg, "getBooleanValue")) {
			Object kb = call(cfg, "getKeybind");
			return new KeyS(name, () -> str(call(kb, "getKeysDisplayString")), code -> {
				call(kb, "clearKeys");
				if (code >= 0) callArg(kb, "addKey", int.class, code);
				updateMalilibKeys();
				save.run();
			});
		}
		if (has(cfg, "getBooleanValue") && has(cfg, "setBooleanValue", boolean.class)) {
			return new BoolS(name, () -> Boolean.TRUE.equals(call(cfg, "getBooleanValue")), v -> {
				callArg(cfg, "setBooleanValue", boolean.class, v);
				save.run();
			});
		}
		if (cls.contains("Color") && has(cfg, "getIntegerValue")) {
			return new ColorS(name, () -> ((Number) call(cfg, "getIntegerValue")).intValue(), v -> {
				callArg(cfg, "setIntegerValue", int.class, v);
				save.run();
			});
		}
		if (has(cfg, "getIntegerValue") && has(cfg, "getMinIntegerValue")) {
			int min = ((Number) call(cfg, "getMinIntegerValue")).intValue();
			int max = ((Number) call(cfg, "getMaxIntegerValue")).intValue();
			if ((long) max - min <= 5000) {
				int step = Math.max(1, (max - min) / 100);
				return new IntS(name, min, max, step, "", () -> ((Number) call(cfg, "getIntegerValue")).intValue(), v -> {
					callArg(cfg, "setIntegerValue", int.class, v);
					save.run();
				});
			}
			return text(name, cfg, save);
		}
		if (has(cfg, "getDoubleValue") && has(cfg, "getMinDoubleValue")) {
			double min = ((Number) call(cfg, "getMinDoubleValue")).doubleValue();
			double max = ((Number) call(cfg, "getMaxDoubleValue")).doubleValue();
			if (max - min <= 1000) {
				// tenths: 0.1 steps
				int lo = (int) Math.round(min * 10), hi = (int) Math.round(max * 10);
				return new IntS(name, lo, hi, Math.max(1, (hi - lo) / 100), "",
						() -> (int) Math.round(((Number) call(cfg, "getDoubleValue")).doubleValue() * 10), v -> {
							callArg(cfg, "setDoubleValue", double.class, v / 10.0);
							save.run();
						}).fmt(v -> String.format(Locale.ROOT, "%.1f", v / 10.0));
			}
			return text(name, cfg, save);
		}
		if (has(cfg, "getOptionListValue")) {
			return new ChoiceS(name, () -> str(call(call(cfg, "getOptionListValue"), "getDisplayName")), fwd -> {
				Object cur = call(cfg, "getOptionListValue");
				Object next = callArg(cur, "cycle", boolean.class, fwd);
				try {
					for (Method m : cfg.getClass().getMethods()) {
						if (m.getName().equals("setOptionListValue") && m.getParameterCount() == 1) {
							m.invoke(cfg, next);
							break;
						}
					}
				} catch (Exception e) {
					FactionsClient.LOG.warn("Couldn't change {}: {}", cfg, e.toString());
				}
				save.run();
			});
		}
		if (cls.equals("ConfigString") && has(cfg, "setValueFromString", String.class)) {
			return text(name, cfg, save);
		}
		return null; // lists, block-state lists...: not editable here
	}

	private static Setting text(String name, Object cfg, Runnable save) {
		String cur = str(call(cfg, "getStringValue"));
		return new TextS(name + (cur == null ? "" : "  (" + cur + ")"), cur == null ? "" : cur, 64, "Set", v -> {
			callArg(cfg, "setValueFromString", String.class, v);
			save.run();
		});
	}

	/** After changing a hotkey malilib needs to rebuild its key map. */
	private static void updateMalilibKeys() {
		try {
			Object mgr = Class.forName("fi.dy.masa.malilib.event.InputEventHandler").getMethod("getKeybindManager").invoke(null);
			mgr.getClass().getMethod("updateUsedKeys").invoke(mgr);
		} catch (Throwable ignored) { }
	}

	// ================================================================ Xaero's Minimap

	private static final String XAERO_OPTIONS = "xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions";

	private static Object xaeroChannel() {
		try {
			Object hud = Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
			return hud == null ? null : hud.getClass().getMethod("getHudConfigs").invoke(hud);
		} catch (Throwable t) {
			return null;
		}
	}

	public static boolean xaeroPresent() {
		return xaeroChannel() != null;
	}

	private static Object xaeroOption(String field) {
		try {
			return Class.forName(XAERO_OPTIONS).getField(field).get(null);
		} catch (Throwable t) {
			return null;
		}
	}

	public static Object xaeroGet(Object option) {
		try {
			Object mgr = call(xaeroChannel(), "getClientConfigManager");
			for (Method m : mgr.getClass().getMethods()) {
				if (m.getName().equals("getEffective") && m.getParameterCount() == 1) return m.invoke(mgr, option);
			}
		} catch (Throwable ignored) { }
		return null;
	}

	public static void xaeroSet(Object option, Object value) {
		try {
			Object ch = xaeroChannel();
			Object mgr = call(ch, "getClientConfigManager");
			Object profile = call(mgr, "getCurrentProfile");
			for (Method m : profile.getClass().getMethods()) {
				if (m.getName().equals("set") && m.getParameterCount() == 2) {
					m.invoke(profile, option, value);
					break;
				}
			}
			Object io = call(ch, "getClientConfigProfileIO");
			for (Method m : io.getClass().getMethods()) {
				if (m.getName().equals("save") && m.getParameterCount() == 1) {
					m.invoke(io, profile);
					break;
				}
			}
		} catch (Throwable t) {
			FactionsClient.LOG.warn("Couldn't change minimap option: {}", t.toString());
		}
	}

	/** Quick toggle for a boolean minimap option by field name (e.g. DISPLAY_MINIMAP). null = not available. */
	public static Boolean xaeroBool(String field) {
		Object o = xaeroOption(field);
		Object v = o == null ? null : xaeroGet(o);
		return v instanceof Boolean b ? b : null;
	}

	public static void setXaeroBool(String field, boolean v) {
		Object o = xaeroOption(field);
		if (o != null) xaeroSet(o, v);
	}

	/** Every minimap option, in Xaero's own order. */
	public static List<Setting> xaeroMinimap() {
		List<Setting> out = new ArrayList<>();
		if (xaeroChannel() == null) {
			out.add(new InfoS(() -> "Xaero's Minimap isn't loaded."));
			return out;
		}
		try {
			for (Field f : Class.forName(XAERO_OPTIONS).getFields()) {
				if (!Modifier.isStatic(f.getModifiers())) continue;
				Object opt = f.get(null);
				if (opt == null || f.getName().equals("DISPLAY_MINIMAP")) continue; // that's the card's own switch
				try {
					Setting s = xaeroOne(opt, f.getName());
					if (s != null) out.add(s);
				} catch (Throwable ignored) { }
			}
		} catch (Throwable t) {
			out.add(new InfoS(() -> "Couldn't read minimap options."));
		}
		return out;
	}

	private static Setting xaeroOne(Object opt, String field) throws Exception {
		String name = displayName(opt, field);
		Object cur = xaeroGet(opt);
		if (cur instanceof Boolean) {
			return new BoolS(name, () -> Boolean.TRUE.equals(xaeroGet(opt)), v -> xaeroSet(opt, v));
		}
		if (has(opt, "getValidValues")) {
			List<?> valid = (List<?>) call(opt, "getValidValues");
			if (valid == null || valid.isEmpty()) return null;
			return new ChoiceS(name, () -> xaeroShow(opt, xaeroGet(opt)), fwd -> {
				int i = valid.indexOf(xaeroGet(opt));
				int n = valid.size();
				int next = i < 0 ? 0 : (i + (fwd ? 1 : n - 1)) % n;
				xaeroSet(opt, valid.get(next));
			});
		}
		return null; // free text / sets / radar categories: Xaero's own screen
	}

	private static String displayName(Object opt, String field) {
		try {
			Object c = call(opt, "getDisplayName");
			if (c instanceof Component comp) {
				String s = comp.getString();
				if (!s.isBlank() && !s.contains(".")) return s;
			}
		} catch (Throwable ignored) { }
		return pretty(field);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String xaeroShow(Object opt, Object value) {
		try {
			Object getter = call(opt, "getDisplayGetter");
			if (getter instanceof java.util.function.BiFunction bf) {
				Object c = bf.apply(opt, value);
				if (c instanceof Component comp) return comp.getString();
			}
		} catch (Throwable ignored) { }
		return String.valueOf(value);
	}

	// ================================================================ helpers

	static String pretty(String id) {
		if (id == null) return "";
		String s = id.replace('_', ' ').replace('.', ' ').toLowerCase(Locale.ROOT).trim();
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String str(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static boolean has(Object o, String method, Class<?>... args) {
		try {
			o.getClass().getMethod(method, args);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	private static Object call(Object o, String method) {
		if (o == null) return null;
		try {
			return o.getClass().getMethod(method).invoke(o);
		} catch (Throwable t) {
			return null;
		}
	}

	private static Object callArg(Object o, String method, Class<?> type, Object arg) {
		if (o == null) return null;
		try {
			return o.getClass().getMethod(method, type).invoke(o, arg);
		} catch (Throwable t) {
			return null;
		}
	}
}
