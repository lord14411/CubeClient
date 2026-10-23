package com.cubecraft.models;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** A decoded texture: ARGB pixels, row 0 on top. */
public final class Tex {
	private static final int MAX = 2048;
	public final int w, h;
	public final int[] argb;
	private byte[] png;

	public Tex(int w, int h, int[] argb) {
		this.w = w;
		this.h = h;
		this.argb = argb;
	}

	/** Decodes PNG / JPEG / BMP / GIF. Returns null for formats Java cannot read (webp, tga, dds...). */
	public static Tex decode(byte[] data) {
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
			if (img == null) return null;
			int w = img.getWidth(), h = img.getHeight();
			if (w <= 0 || h <= 0) return null;
			int[] px = img.getRGB(0, 0, w, h, null, 0, w);
			if (w > MAX || h > MAX) { // keep video memory sane: nearest-neighbour down to at most 2048
				int step = Math.max((w + MAX - 1) / MAX, (h + MAX - 1) / MAX);
				int nw = w / step, nh = h / step;
				int[] small = new int[nw * nh];
				for (int y = 0; y < nh; y++) for (int x = 0; x < nw; x++) small[y * nw + x] = px[(y * step) * w + x * step];
				return new Tex(nw, nh, small);
			}
			return new Tex(w, h, px);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** PNG bytes for uploading to the game (cached). */
	public synchronized byte[] png() {
		if (png == null) {
			try {
				BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				img.setRGB(0, 0, w, h, argb, 0, w);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(img, "png", out);
				png = out.toByteArray();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return png;
	}

	/** Nearest sample with repeat wrapping; v = 0 is the top row. */
	public int sample(float u, float v) {
		float fu = u - (float) Math.floor(u), fv = v - (float) Math.floor(v);
		int x = Math.min(w - 1, (int) (fu * w)), y = Math.min(h - 1, (int) (fv * h));
		return argb[y * w + x];
	}
}
