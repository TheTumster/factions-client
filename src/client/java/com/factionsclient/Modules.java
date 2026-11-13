package com.factionsclient;

import java.util.ArrayDeque;
import java.util.Deque;

import com.factionsclient.mixin.OptionInstanceAccessor;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/** Small per-tick/per-frame modules: fullbright, zoom, toggle sprint, CPS counter. */
public final class Modules {
	private static final double FULLBRIGHT_GAMMA = 16.0;
	private static Double savedGamma = null;
	private static Integer savedFov = null;
	private static boolean sprintPressedByUs = false;

	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();
	private static boolean leftWasDown;
	private static boolean rightWasDown;

	private Modules() { }

	@SuppressWarnings("unchecked")
	private static <T> void setRaw(OptionInstance<T> opt, T value) {
		((OptionInstanceAccessor) (Object) opt).factionsclient$setRaw(value);
	}

	public static void tick(Minecraft mc, KeyMapping zoomKey) {
		Config cfg = FactionsClient.CONFIG;

		// Fullbright: push gamma way past the slider max, put it back when turned off.
		OptionInstance<Double> gamma = mc.options.gamma();
		if (cfg.fullbright) {
			if (savedGamma == null) savedGamma = Math.min(gamma.get(), 1.0);
			if (gamma.get() != FULLBRIGHT_GAMMA) setRaw(gamma, FULLBRIGHT_GAMMA);
		} else if (savedGamma != null) {
			setRaw(gamma, savedGamma);
			savedGamma = null;
		}

		// Zoom (hold key, like OptiFine's C)
		OptionInstance<Integer> fov = mc.options.fov();
		boolean zooming = zoomKey.isDown() && mc.screen == null;
		if (zooming) {
			if (savedFov == null) savedFov = fov.get();
			if (fov.get() != cfg.zoomFov) setRaw(fov, cfg.zoomFov);
		} else if (savedFov != null) {
			setRaw(fov, savedFov);
			savedFov = null;
		}

		// Toggle sprint: hold sprint for you whenever you're walking forward.
		if (mc.player != null && cfg.toggleSprint) {
			boolean walking = mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.isUsingItem();
			if (walking) {
				mc.options.keySprint.setDown(true);
				sprintPressedByUs = true;
			} else if (sprintPressedByUs) {
				mc.options.keySprint.setDown(false);
				sprintPressedByUs = false;
			}
		} else if (sprintPressedByUs) {
			mc.options.keySprint.setDown(false);
			sprintPressedByUs = false;
		}
	}

	/** Called every frame from the HUD so fast clicks are counted. */
	public static void sampleClicks(Minecraft mc) {
		long now = System.currentTimeMillis();
		boolean l = mc.options.keyAttack.isDown();
		boolean r = mc.options.keyUse.isDown();
		if (l && !leftWasDown) LEFT.addLast(now);
		if (r && !rightWasDown) RIGHT.addLast(now);
		leftWasDown = l;
		rightWasDown = r;
		while (!LEFT.isEmpty() && now - LEFT.peekFirst() > 1000) LEFT.removeFirst();
		while (!RIGHT.isEmpty() && now - RIGHT.peekFirst() > 1000) RIGHT.removeFirst();
	}

	public static int leftCps() {
		return LEFT.size();
	}

	public static int rightCps() {
		return RIGHT.size();
	}

	/** Make sure options.txt never gets saved with fullbright/zoom values. */
	public static void restoreAll(Minecraft mc) {
		if (savedGamma != null) setRaw(mc.options.gamma(), savedGamma);
		if (savedFov != null) setRaw(mc.options.fov(), savedFov);
		savedGamma = null;
		savedFov = null;
	}
}
