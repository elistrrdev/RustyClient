package com.rustyclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

public final class Tex {
	private static final AtomicInteger COUNTER = new AtomicInteger();

	public final Identifier id;
	public final int width;
	public final int height;
	private final DynamicTexture texture;

	private Tex(String kind, NativeImage image) {
		this.width = image.getWidth();
		this.height = image.getHeight();
		this.id = Identifier.fromNamespaceAndPath("rustyclient", "dynamic/" + kind + "_" + COUNTER.incrementAndGet());
		this.texture = new DynamicTexture(id::toString, image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
	}

	public static Tex alpha(String kind, int width, int height, float[] coverage) {
		NativeImage image = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int a = Math.round(Math.max(0f, Math.min(1f, coverage[y * width + x])) * 255f);
				image.setPixel(x, y, (a << 24) | 0xFFFFFF);
			}
		}
		return new Tex(kind, image);
	}

	public static Tex image(String kind, BufferedImage source) {
		NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
		copy(source, image, 0, 0, source.getWidth(), source.getHeight());
		return new Tex(kind, image);
	}

	public static Tex blank(String kind, int width, int height) {
		NativeImage image = new NativeImage(width, height, true);
		return new Tex(kind, image);
	}

	public void write(BufferedImage source, int x, int y, int w, int h) {
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}
		copy(source, image, x, y, w, h);
	}

	public void upload() {
		texture.upload();
	}

	public void close() {
		Minecraft.getInstance().getTextureManager().release(id);
	}

	private static void copy(BufferedImage source, NativeImage image, int x0, int y0, int w, int h) {
		int[] row = new int[w];
		for (int y = y0; y < y0 + h; y++) {
			source.getRGB(x0, y, w, 1, row, 0, w);
			for (int i = 0; i < w; i++) {
				int argb = row[i];
				int a = argb >>> 24;
				image.setPixel(x0 + i, y, (a << 24) | 0xFFFFFF);
			}
		}
	}
}
