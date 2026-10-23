package com.cubecraft;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Loads your own 64x64 skin PNGs from config/cubecraft/skins/.
 * Name a file "something_slim.png" to use the slim (Alex-style) arms.
 */
public final class CustomSkins {
	private static final Map<String, PlayerSkin> CACHE = new HashMap<>();
	private static List<String> names = new ArrayList<>();

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("cubecraft").resolve("skins");
	}

	/** Re-reads the folder. Call when opening the menu or pressing Reload. */
	public static void reload() {
		List<String> found = new ArrayList<>();
		try {
			Files.createDirectories(dir());
			try (Stream<Path> s = Files.list(dir())) {
				s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
					.sorted().forEach(p -> found.add(p.getFileName().toString()));
			}
		} catch (IOException e) {
			System.err.println("[cubecraft] cannot read skins folder: " + e);
		}
		names = found;
	}

	public static List<String> names() {
		return names;
	}

	/** Returns the skin for a file name, loading and registering the texture the first time. null if it can't be used. */
	public static PlayerSkin get(String file) {
		if (file == null || file.isEmpty()) return null;
		PlayerSkin cached = CACHE.get(file);
		if (cached != null) return cached;
		Path p = dir().resolve(file);
		if (!Files.isRegularFile(p)) return null;
		try (InputStream in = Files.newInputStream(p)) {
			NativeImage img = NativeImage.read(in);
			if (img.getWidth() != 64 || img.getHeight() != 64) {
				System.err.println("[cubecraft] " + file + " must be a 64x64 skin (old 64x32 skins are not supported)");
				img.close();
				return null;
			}
			String id = "custom/" + file.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
			Identifier rl = Identifier.fromNamespaceAndPath("cubecraft", id);
			Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(() -> id, img));
			PlayerSkin base = DefaultPlayerSkin.get(new UUID(0L, 0L));
			boolean slim = file.toLowerCase(Locale.ROOT).contains("slim");
			PlayerSkin skin = new PlayerSkin(new ClientAsset.ResourceTexture(rl, rl), base.cape(), base.elytra(),
				slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, true);
			CACHE.put(file, skin);
			return skin;
		} catch (IOException | RuntimeException e) {
			System.err.println("[cubecraft] failed to load skin " + file + ": " + e);
			return null;
		}
	}

	private CustomSkins() {
	}
}
