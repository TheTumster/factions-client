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

		if (cfg.chunkGrid) chunkBorders(m, vc, cam, mc);

		if (cfg.team) {
			String dim = TeamSync.dimId(mc);
			for (TeamSync.Mate mate : TeamSync.MATES) {
				if (!mate.dim().equals(dim)) continue;
				// Tall thin pillar so you can spot them from far away
				box(m, vc, cam, mate.x() - 0.15, mate.y(), mate.z() - 0.15, mate.x() + 0.15, mate.y() + 40, mate.z() + 0.15, MATE);
			}
		}
	}

	private static final int CHUNK_EDGE = 0xC0FFE040;   // yellow: your chunk
	private static final int CHUNK_GRID = 0x50FFE040;   // faint grid on your chunk's walls
	private static final int CHUNK_CORNER = 0xC0FF3030; // red: neighbor chunk corners

	/** Factions claim view: current chunk walls with a grid, corner posts for the 3x3 around you. */
	private static void chunkBorders(Matrix4f m, VertexConsumer vc, Vec3 cam, Minecraft mc) {
		int cx = mc.player.getBlockX() >> 4;
		int cz = mc.player.getBlockZ() >> 4;
		double x0 = cx << 4, z0 = cz << 4, x1 = x0 + 16, z1 = z0 + 16;
		double py = Math.floor(mc.player.getY());
		double yMin = Math.max(mc.level.getMinY(), py - 24);
		double yMax = py + 24;
		double t = 0.025;

		// Vertical lines along your chunk's walls (thicker at the corners)
		for (int i = 0; i <= 16; i++) {
			double w = (i == 0 || i == 16) ? 0.06 : t;
			int col = (i == 0 || i == 16) ? CHUNK_EDGE : CHUNK_GRID;
			box(m, vc, cam, x0 + i - w, yMin, z0 - w, x0 + i + w, yMax, z0 + w, col);
			box(m, vc, cam, x0 + i - w, yMin, z1 - w, x0 + i + w, yMax, z1 + w, col);
			box(m, vc, cam, x0 - w, yMin, z0 + i - w, x0 + w, yMax, z0 + i + w, col);
			box(m, vc, cam, x1 - w, yMin, z0 + i - w, x1 + w, yMax, z0 + i + w, col);
		}
		// Horizontal lines every 2 blocks
		for (double y = Math.ceil(yMin / 2) * 2; y <= yMax; y += 2) {
			int col = y == py ? CHUNK_EDGE : CHUNK_GRID;
			box(m, vc, cam, x0, y - t, z0 - t, x1, y + t, z0 + t, col);
			box(m, vc, cam, x0, y - t, z1 - t, x1, y + t, z1 + t, col);
			box(m, vc, cam, x0 - t, y - t, z0, x0 + t, y + t, z1, col);
			box(m, vc, cam, x1 - t, y - t, z0, x1 + t, y + t, z1, col);
		}
		// Red corner posts for the surrounding chunks
		for (int dx = -1; dx <= 2; dx++) {
			for (int dz = -1; dz <= 2; dz++) {
				double x = x0 + dx * 16, z = z0 + dz * 16;
				if ((dx == 0 || dx == 1) && (dz == 0 || dz == 1)) continue; // your own corners
				box(m, vc, cam, x - 0.05, yMin, z - 0.05, x + 0.05, yMax, z + 0.05, CHUNK_CORNER);
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
