package com.factionsclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import com.factionsclient.ModuleRegistry.BoolS;
import com.factionsclient.ModuleRegistry.ChoiceS;
import com.factionsclient.ModuleRegistry.InfoS;
import com.factionsclient.ModuleRegistry.IntS;
import com.factionsclient.ModuleRegistry.Setting;

/**
 * Video settings in our UI, plus a one-time tune on first launch: clouds off, render distance up to 16,
 * FPS cap lifted. Nothing that makes the game look worse.
 */
public final class Performance {
	private Performance() { }

	/** First launch with this version: free FPS without nerfing visuals. */
	public static void tuneOnce(Minecraft mc) {
		Config c = FactionsClient.CONFIG;
		if (c.perfTuned) return;
		Options o = mc.options;
		try {
			o.cloudStatus().set(CloudStatus.OFF);
			if (o.renderDistance().get() < 16) o.renderDistance().set(16);
			if (!o.enableVsync().get() && o.framerateLimit().get() <= 120) o.framerateLimit().set(260); // 260 = unlimited
			o.save();
			FactionsClient.LOG.info("Performance tune applied: clouds off, render distance {}, fps cap {}",
					o.renderDistance().get(), o.framerateLimit().get());
		} catch (Throwable t) {
			FactionsClient.LOG.warn("Performance tune failed: {}", t.toString());
		}
		c.perfTuned = true;
		c.save();
	}

	static List<Setting> settings() {
		Options o = Minecraft.getInstance().options;
		List<Setting> s = new ArrayList<>();
		s.add(new IntS("Render distance", 2, 32, 1, " chunks", () -> o.renderDistance().get(), v -> set(o.renderDistance(), v)));
		s.add(new IntS("Simulation distance", 5, 32, 1, " chunks", () -> o.simulationDistance().get(), v -> set(o.simulationDistance(), v)));
		s.add(new IntS("Max FPS", 10, 260, 10, "", () -> o.framerateLimit().get(), v -> set(o.framerateLimit(), v))
				.fmt(v -> v >= 260 ? "Unlimited" : v + " fps"));
		s.add(new BoolS("VSync", () -> o.enableVsync().get(), v -> set(o.enableVsync(), v)));
		s.add(choice("Clouds", o.cloudStatus()));
		s.add(choice("Graphics", o.graphicsPreset()));
		s.add(choice("Particles", o.particles()));
		s.add(new BoolS("Smooth lighting", () -> o.ambientOcclusion().get(), v -> set(o.ambientOcclusion(), v)));
		s.add(new BoolS("Entity shadows", () -> o.entityShadows().get(), v -> set(o.entityShadows(), v)));
		s.add(new IntS("Entity distance", 50, 500, 25, "%", () -> (int) Math.round(o.entityDistanceScaling().get() * 100),
				v -> set(o.entityDistanceScaling(), v / 100.0)));
		s.add(new IntS("Biome blend", 0, 7, 1, "", () -> o.biomeBlendRadius().get(), v -> set(o.biomeBlendRadius(), v))
				.fmt(v -> v == 0 ? "Off" : (2 * v + 1) + "x" + (2 * v + 1)));
		s.add(new IntS("Mipmap levels", 0, 4, 1, "", () -> o.mipmapLevels().get(), v -> set(o.mipmapLevels(), v)));
		s.add(new IntS("FOV", 30, 110, 1, "°", () -> o.fov().get(), v -> set(o.fov(), v)));
		s.add(new IntS("Brightness", 0, 100, 5, "%", () -> (int) Math.round(o.gamma().get() * 100), v -> set(o.gamma(), v / 100.0)));
		s.add(new BoolS("View bobbing", () -> o.bobView().get(), v -> set(o.bobView(), v)));
		s.add(new InfoS(() -> "Chunk caching (Bobby) keeps chunks you've seen, so far terrain stays loaded."));
		return s;
	}

	private static <T> void set(OptionInstance<T> opt, T v) {
		opt.set(v);
		Minecraft.getInstance().options.save();
	}

	/** Cycle any enum option (skips CUSTOM, which is just "you changed something yourself"). */
	@SuppressWarnings("unchecked")
	private static <T> ChoiceS choice(String name, OptionInstance<T> opt) {
		return new ChoiceS(name, () -> nice(opt.get()), fwd -> {
			Object cur = opt.get();
			if (!(cur instanceof Enum<?> e)) return;
			Object[] all = e.getDeclaringClass().getEnumConstants();
			int i = e.ordinal();
			for (int k = 0; k < all.length; k++) {
				i = (i + (fwd ? 1 : all.length - 1)) % all.length;
				if (!((Enum<?>) all[i]).name().equals("CUSTOM")) break;
			}
			set(opt, (T) all[i]);
		});
	}

	private static String nice(Object v) {
		if (v instanceof Enum<?> e) {
			String n = e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
			return Character.toUpperCase(n.charAt(0)) + n.substring(1);
		}
		return String.valueOf(v);
	}
}
