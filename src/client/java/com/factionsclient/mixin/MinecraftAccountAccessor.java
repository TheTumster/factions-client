package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;

/** Account switcher: swap the logged-in user (and drop chat-signing keys for offline names). */
@Mixin(Minecraft.class)
public interface MinecraftAccountAccessor {
	@Mutable
	@Accessor("user")
	void factionsclient$setUser(User user);

	@Accessor("profileKeyPairManager")
	ProfileKeyPairManager factionsclient$getKeys();

	@Mutable
	@Accessor("profileKeyPairManager")
	void factionsclient$setKeys(ProfileKeyPairManager keys);
}
