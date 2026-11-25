package com.factionsclient;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Freelook (Lunar's "perspective"): hold the key to look around in third person without turning. */
public final class Freelook {
	public static float yaw, pitch;
	private static boolean held;
	private static CameraType before;

	private Freelook() { }

	public static boolean active() {
		return held;
	}

	public static void tick(Minecraft mc) {
		boolean down = FactionsClient.CONFIG.freelook && FactionsClient.freelookKey != null && FactionsClient.freelookKey.isDown()
				&& mc.player != null && mc.screen == null;
		if (down && !held) {
			held = true;
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
			before = mc.options.getCameraType();
			if (FactionsClient.CONFIG.freelookThirdPerson) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		} else if (!down && held) {
			held = false;
			if (before != null) mc.options.setCameraType(before);
			before = null;
		}
	}

	/** Same maths as Entity.turn, applied to the camera only. */
	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15F;
		pitch = Mth.clamp(pitch + (float) dy * 0.15F, -90.0F, 90.0F);
	}
}
