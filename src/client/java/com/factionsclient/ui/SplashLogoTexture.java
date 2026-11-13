package com.factionsclient.ui;

import java.io.IOException;
import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Logo for the loading screen. Read straight from our jar because resource packs aren't loaded yet. */
public class SplashLogoTexture extends ReloadableTexture {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("factionsclient", "splash_logo");

	public SplashLogoTexture() {
		super(ID);
	}

	@Override
	public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
		try (InputStream in = SplashLogoTexture.class.getResourceAsStream("/assets/factionsclient/textures/gui/logo.png")) {
			if (in == null) throw new IOException("laG logo missing from jar");
			return new TextureContents(NativeImage.read(in), new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0.0F));
		}
	}
}
