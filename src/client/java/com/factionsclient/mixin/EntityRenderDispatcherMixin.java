package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;

import com.factionsclient.FactionsClient;

/** Factions Visuals: don't draw TNT / falling sand entities (crumbs + shot tracer still show where they go). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void factionsclient$hide(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		var c = FactionsClient.CONFIG;
		if (c == null) return;
		if ((c.hideTnt && entity instanceof PrimedTnt) || (c.hideSand && entity instanceof FallingBlockEntity)) cir.setReturnValue(false);
	}
}
