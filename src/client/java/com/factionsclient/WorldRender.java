package com.factionsclient;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/** Draws crumb trails, explosion markers and teammate beacons in the world. */
public final class WorldRender {
	private static final int TNT_TRAIL = 0xB0FF5A1F;   // orange-red
	private static final int SAND_TRAIL = 0xB0F2D16B;  // sand yellow
	private static final int MY_BOOM = 0x90FF1010;     // red
	private static final int TEAM_BOOM = 0x90B040FF;   // purple (teammate saw it)
	private static final int MATE = 0x6038E0FF;        // cyan

	private WorldRender() { }

	public static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Config cfg = FactionsClient.CONFIG;

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		VertexConsumer vc = context.consumers().getBuffer(RenderTypes.debugFilledBox());
		Matrix4f m = matrices.last().pose();

		if (cfg.crumbs) {
			for (Crumbs.Trail t : Crumbs.ACTIVE.values()) trail(m, vc, cam, t);
			for (Crumbs.Trail t : Crumbs.FINISHED) trail(m, vc, cam, t);
			for (Crumbs.Marker mk : Crumbs.MARKERS) {
				// Explosion spot: highlight the whole block
				double bx = Math.floor(mk.pos().x), by = Math.floor(mk.pos().y), bz = Math.floor(mk.pos().z);
				box(m, vc, cam, bx - 0.01, by - 0.01, bz - 0.01, bx + 1.01, by + 1.01, bz + 1.01, mk.shared() ? TEAM_BOOM : MY_BOOM);
			}
		}

		if (cfg.team) {
			String dim = TeamSync.dimId(mc);
			for (TeamSync.Mate mate : TeamSync.MATES) {
				if (!mate.dim().equals(dim)) continue;
				// Tall thin pillar so you can spot them from far away
				box(m, vc, cam, mate.x() - 0.15, mate.y(), mate.z() - 0.15, mate.x() + 0.15, mate.y() + 40, mate.z() + 0.15, MATE);
			}
		}
	}

	private static void trail(Matrix4f m, VertexConsumer vc, Vec3 cam, Crumbs.Trail t) {
		int color = t.tnt ? TNT_TRAIL : SAND_TRAIL;
		double s = t.tnt ? 0.09 : 0.07;
		for (Vec3 p : t.points) {
			// Entity position is the bottom-center; lift the dot to the middle of the block-sized entity
			double y = p.y + 0.49;
			box(m, vc, cam, p.x - s, y - s, p.z - s, p.x + s, y + s, p.z + s, color);
		}
	}

	/** Filled box in world coordinates (converted to camera-relative to keep float precision). */
	private static void box(Matrix4f m, VertexConsumer vc, Vec3 cam,
			double x1d, double y1d, double z1d, double x2d, double y2d, double z2d, int c) {
		float x1 = (float) (x1d - cam.x), y1 = (float) (y1d - cam.y), z1 = (float) (z1d - cam.z);
		float x2 = (float) (x2d - cam.x), y2 = (float) (y2d - cam.y), z2 = (float) (z2d - cam.z);

		// Front (min Z)
		vc.addVertex(m, x1, y1, z1).setColor(c);
		vc.addVertex(m, x2, y1, z1).setColor(c);
		vc.addVertex(m, x2, y2, z1).setColor(c);
		vc.addVertex(m, x1, y2, z1).setColor(c);
		// Back (max Z)
		vc.addVertex(m, x2, y1, z2).setColor(c);
		vc.addVertex(m, x1, y1, z2).setColor(c);
		vc.addVertex(m, x1, y2, z2).setColor(c);
		vc.addVertex(m, x2, y2, z2).setColor(c);
		// Left (min X)
		vc.addVertex(m, x1, y1, z2).setColor(c);
		vc.addVertex(m, x1, y1, z1).setColor(c);
		vc.addVertex(m, x1, y2, z1).setColor(c);
		vc.addVertex(m, x1, y2, z2).setColor(c);
		// Right (max X)
		vc.addVertex(m, x2, y1, z1).setColor(c);
		vc.addVertex(m, x2, y1, z2).setColor(c);
		vc.addVertex(m, x2, y2, z2).setColor(c);
		vc.addVertex(m, x2, y2, z1).setColor(c);
		// Top
		vc.addVertex(m, x1, y2, z1).setColor(c);
		vc.addVertex(m, x2, y2, z1).setColor(c);
		vc.addVertex(m, x2, y2, z2).setColor(c);
		vc.addVertex(m, x1, y2, z2).setColor(c);
		// Bottom
		vc.addVertex(m, x1, y1, z2).setColor(c);
		vc.addVertex(m, x2, y1, z2).setColor(c);
		vc.addVertex(m, x2, y1, z1).setColor(c);
		vc.addVertex(m, x1, y1, z1).setColor(c);
	}
}
