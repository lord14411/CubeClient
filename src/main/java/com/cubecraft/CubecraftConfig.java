package com.cubecraft;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Client-side settings, saved to config/cubecraft.properties. */
public final class CubecraftConfig {
	/** -1 = use your real skin, otherwise 0..17 = built-in default skin. */
	public static int skin = -1;
	/** File name of a custom skin in config/cubecraft/skins, empty = none. */
	public static String customSkin = "";
	public static boolean skyOn = false;
	public static boolean fogOn = false;
	public static boolean fullbright = false;
	public static boolean stars = false;
	public static boolean shooting = false;
	public static boolean aurora = false;
	public static int starDensity = 60; // 0..100
	/** Cosmic skybox: 0 = off, otherwise index into Cosmos.NAMES. */
	public static int cosmos = 0;
	public static int cosmosBright = 100; // 0..100
	public static boolean cosmosSpin = true;
	/** Client-side time lock, -1 = off. */
	public static int lockTime = -1;
	/** Client-side weather: 0 = server, 1 clear, 2 rain, 3 storm. */
	public static int weather = 0;
	public static int skyColor = 0x78A7FF; // RGB
	public static int fogColor = 0xC0D8FF; // RGB
	/** 3D models: relative path of the selected file inside config/cubecraft/models, empty = none. */
	public static String modelFile = "";
	/** Show the selected model on you (third person). */
	public static boolean modelWear = false;
	/** Make your own body invisible while the worn model is on screen. */
	public static boolean hideBody = false;
	/** 0 = auto, 1..3 = solid render hook A/B/C, 4 = particle hologram. */
	public static int modelRender = 0;

	public static void resetAll() {
		skin = -1;
		customSkin = "";
		skyOn = false;
		fogOn = false;
		fullbright = false;
		stars = false;
		shooting = false;
		aurora = false;
		starDensity = 60;
		cosmos = 0;
		cosmosBright = 100;
		cosmosSpin = true;
		lockTime = -1;
		weather = 0;
		skyColor = 0x78A7FF;
		fogColor = 0xC0D8FF;
		modelFile = "";
		modelWear = false;
		hideBody = false;
		modelRender = 0;
		Models.resetPrefs();
		save();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("cubecraft.properties");
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) return;
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(f)) {
			p.load(in);
			skin = Integer.parseInt(p.getProperty("skin", "-1"));
			customSkin = p.getProperty("customSkin", "");
			skyOn = Boolean.parseBoolean(p.getProperty("skyOn", "false"));
			fogOn = Boolean.parseBoolean(p.getProperty("fogOn", "false"));
			fullbright = Boolean.parseBoolean(p.getProperty("fullbright", "false"));
			stars = Boolean.parseBoolean(p.getProperty("stars", "false"));
			shooting = Boolean.parseBoolean(p.getProperty("shooting", "false"));
			aurora = Boolean.parseBoolean(p.getProperty("aurora", "false"));
			starDensity = Integer.parseInt(p.getProperty("starDensity", "60"));
			cosmos = Math.max(0, Math.min(Cosmos.NAMES.length - 1, Integer.parseInt(p.getProperty("cosmos", "0"))));
			cosmosBright = Math.max(0, Math.min(100, Integer.parseInt(p.getProperty("cosmosBright", "100"))));
			cosmosSpin = Boolean.parseBoolean(p.getProperty("cosmosSpin", "true"));
			lockTime = Integer.parseInt(p.getProperty("lockTime", "-1"));
			weather = Integer.parseInt(p.getProperty("weather", "0"));
			skyColor = Integer.parseInt(p.getProperty("skyColor", "7907327")) & 0xFFFFFF;
			fogColor = Integer.parseInt(p.getProperty("fogColor", "12638463")) & 0xFFFFFF;
		} catch (IOException | NumberFormatException ignored) {
		}
		// models are read separately so a bad number above can never hide them (and the other way round)
		modelFile = p.getProperty("modelFile", "");
		modelWear = Boolean.parseBoolean(p.getProperty("modelWear", "false"));
		hideBody = Boolean.parseBoolean(p.getProperty("hideBody", "false"));
		try {
			modelRender = Math.max(0, Math.min(4, Integer.parseInt(p.getProperty("modelRender", "0"))));
		} catch (NumberFormatException ignored) {
		}
		Models.loadPrefs(p);
	}

	public static void save() {
		Properties p = new Properties();
		p.setProperty("skin", Integer.toString(skin));
		p.setProperty("customSkin", customSkin);
		p.setProperty("skyOn", Boolean.toString(skyOn));
		p.setProperty("fogOn", Boolean.toString(fogOn));
		p.setProperty("fullbright", Boolean.toString(fullbright));
		p.setProperty("stars", Boolean.toString(stars));
		p.setProperty("shooting", Boolean.toString(shooting));
		p.setProperty("aurora", Boolean.toString(aurora));
		p.setProperty("starDensity", Integer.toString(starDensity));
		p.setProperty("cosmos", Integer.toString(cosmos));
		p.setProperty("cosmosBright", Integer.toString(cosmosBright));
		p.setProperty("cosmosSpin", Boolean.toString(cosmosSpin));
		p.setProperty("lockTime", Integer.toString(lockTime));
		p.setProperty("weather", Integer.toString(weather));
		p.setProperty("skyColor", Integer.toString(skyColor));
		p.setProperty("fogColor", Integer.toString(fogColor));
		p.setProperty("modelFile", modelFile);
		p.setProperty("modelWear", Boolean.toString(modelWear));
		p.setProperty("hideBody", Boolean.toString(hideBody));
		p.setProperty("modelRender", Integer.toString(modelRender));
		Models.savePrefs(p);
		try (OutputStream out = Files.newOutputStream(file())) {
			p.store(out, "CubeCraft Studio");
		} catch (IOException ignored) {
		}
	}

	private CubecraftConfig() {
	}
}
