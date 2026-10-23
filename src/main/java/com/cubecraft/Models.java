package com.cubecraft;

import com.cubecraft.models.GltfLoader;
import com.cubecraft.models.ModelData;
import com.cubecraft.models.ObjLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * 3D model support: finds .obj / .glb / .gltf files in config/cubecraft/models, loads them on a background thread,
 * and keeps the list of models you placed (props) plus the one you are wearing. Drawing is in ModelRender / ModelHologram.
 */
public final class Models {
	public static final int LOADING = 0, READY = 1, FAILED = 2;

	/** Per-model settings, remembered between sessions. */
	public static final class Pref {
		public float scale = 1f;
		/** Quarter turns (0..3) added to the model's facing. */
		public int turn = 0;
		/** The file uses Z as "up" (Blender / CAD style) and needs standing on its feet. */
		public boolean zup = false;
	}

	public static final class Entry {
		public volatile int state = LOADING;
		public volatile ModelData data;
		public volatile String error = "";
	}

	/** One model standing in the world (yawDeg is the rotation about Y; the model's front is +Z at 0). */
	public record Inst(String file, double x, double y, double z, float yawDeg, float scale, boolean zup) {
	}

	private static final Logger LOG = LoggerFactory.getLogger("cubecraft");
	private static final int MAX_PROPS = 24;
	private static final Map<String, Pref> PREFS = new HashMap<>();
	private static final Map<String, Entry> CACHE = new HashMap<>();
	public static final List<Inst> PROPS = new ArrayList<>();
	private static List<String> names = new ArrayList<>();
	private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "cubecraft-models");
		t.setDaemon(true);
		return t;
	});

	private static Object lastLevel;
	private static boolean hidApplied = false;
	/** Ticks during which there was something to show; the particle fallback waits for this. */
	public static int activeTicks = 0;

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("cubecraft").resolve("models");
	}

	// ------------------------------------------------------------------ files

	public static void rescan() {
		List<String> found = new ArrayList<>();
		try {
			Files.createDirectories(dir());
			try (Stream<Path> s = Files.walk(dir(), 3)) {
				s.filter(Files::isRegularFile).forEach(p -> {
					String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
					if (n.endsWith(".obj") || n.endsWith(".glb") || n.endsWith(".gltf")) {
						found.add(dir().relativize(p).toString().replace('\\', '/'));
					}
				});
			}
		} catch (IOException e) {
			LOG.warn("[cubecraft] cannot read models folder: " + e);
		}
		found.sort(String.CASE_INSENSITIVE_ORDER);
		names = found;
	}

	public static List<String> names() {
		return names;
	}

	/** Forget loaded models so edited files are read again next time they are needed. */
	public static void dropCache() {
		CACHE.clear();
		ModelRender.forget();
	}

	public static Pref pref(String name) {
		return PREFS.computeIfAbsent(name, k -> new Pref());
	}

	/** Returns the loaded model, starting a background load the first time. Never blocks. */
	public static Entry get(String name) {
		Entry e = CACHE.get(name);
		if (e != null) return e;
		final Entry fresh = new Entry();
		CACHE.put(name, fresh);
		POOL.execute(() -> {
			try {
				Path p = dir().resolve(name).normalize();
				if (!p.startsWith(dir().normalize()) || !Files.isRegularFile(p)) throw new IOException("file not found: " + name);
				String low = name.toLowerCase(Locale.ROOT);
				ModelData d = low.endsWith(".obj") ? ObjLoader.load(p) : GltfLoader.load(p);
				fresh.data = d;
				fresh.state = READY;
				LOG.info("[cubecraft] loaded model " + name + " (" + d.triangles + " triangles, " + d.parts.size() + " materials)");
			} catch (Throwable t) {
				String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
				if (t instanceof OutOfMemoryError) msg = "out of memory (model too big)";
				fresh.error = msg;
				fresh.state = FAILED;
				LOG.warn("[cubecraft] failed to load model " + name + ": " + msg);
			}
		});
		return fresh;
	}

	// ------------------------------------------------------------------ settings storage

	static void loadPrefs(Properties p) {
		for (String key : p.stringPropertyNames()) {
			if (!key.startsWith("model.")) continue;
			int dot = key.lastIndexOf('.');
			if (dot <= 6) continue;
			String name = key.substring(6, dot), field = key.substring(dot + 1), val = p.getProperty(key);
			try {
				Pref pr = pref(name);
				switch (field) {
					case "scale" -> pr.scale = Math.max(0.01f, Math.min(100f, Float.parseFloat(val)));
					case "turn" -> pr.turn = Math.floorMod(Integer.parseInt(val), 4);
					case "zup" -> pr.zup = Boolean.parseBoolean(val);
					default -> {
					}
				}
			} catch (NumberFormatException ignored) {
			}
		}
	}

	static void savePrefs(Properties p) {
		for (Map.Entry<String, Pref> e : PREFS.entrySet()) {
			Pref pr = e.getValue();
			if (pr.scale == 1f && pr.turn == 0 && !pr.zup) continue;
			p.setProperty("model." + e.getKey() + ".scale", Float.toString(pr.scale));
			p.setProperty("model." + e.getKey() + ".turn", Integer.toString(pr.turn));
			p.setProperty("model." + e.getKey() + ".zup", Boolean.toString(pr.zup));
		}
	}

	static void resetPrefs() {
		PREFS.clear();
		PROPS.clear();
	}

	// ------------------------------------------------------------------ placing and wearing

	/** Puts the selected model in front of you, facing you. Returns a message for the action bar. */
	public static String place(Minecraft mc) {
		if (mc.player == null) return "Join a world first.";
		String f = CubecraftConfig.modelFile;
		if (f.isEmpty()) return "Pick a model first.";
		Entry e = get(f);
		if (e.state == LOADING) return "Still loading, try again in a moment...";
		if (e.state == FAILED) return "Could not load: " + e.error;
		Pref pr = pref(f);
		float yaw = mc.player.getYRot();
		double rad = Math.toRadians(yaw);
		double x = mc.player.getX() - Math.sin(rad) * 2.5;
		double z = mc.player.getZ() + Math.cos(rad) * 2.5;
		if (PROPS.size() >= MAX_PROPS) PROPS.remove(0);
		PROPS.add(new Inst(f, x, mc.player.getY(), z, 180f - yaw + 90f * pr.turn, pr.scale, pr.zup));
		return "Placed " + f;
	}

	public static float partial(Minecraft mc) {
		return mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	private static double lerp(float t, double a, double b) {
		return a + (b - a) * t;
	}

	private static float lerpAngle(float t, float a, float b) {
		float d = ((b - a + 540f) % 360f) - 180f;
		return a + d * t;
	}

	/** Everything to draw this frame: placed props plus the worn model (hidden in first person, where it would be around your eyes). */
	public static List<Inst> collect(Minecraft mc, float pt) {
		List<Inst> out = new ArrayList<>(PROPS);
		if (mc.player != null && CubecraftConfig.modelWear && !CubecraftConfig.modelFile.isEmpty()
			&& !mc.options.getCameraType().isFirstPerson()) {
			Pref pr = pref(CubecraftConfig.modelFile);
			var p = mc.player;
			float body = lerpAngle(pt, p.yBodyRotO, p.yBodyRot);
			out.add(new Inst(CubecraftConfig.modelFile, lerp(pt, p.xo, p.getX()), lerp(pt, p.yo, p.getY()), lerp(pt, p.zo, p.getZ()),
				-body + 90f * pr.turn, pr.scale, pr.zup));
		}
		return out;
	}

	// ------------------------------------------------------------------ per tick

	public static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			PROPS.clear();
			lastLevel = mc.level;
			hidApplied = false;
		}
		if (mc.player == null) {
			activeTicks = 0;
			return;
		}
		boolean wearing = CubecraftConfig.modelWear && !CubecraftConfig.modelFile.isEmpty();
		boolean wornReady = false;
		if (wearing) wornReady = get(CubecraftConfig.modelFile).state == READY;
		for (Inst i : PROPS) get(i.file()); // reload after a cache drop

		boolean any = wornReady || !PROPS.isEmpty();
		activeTicks = any ? activeTicks + 1 : 0;

		// optional: hide your own body while the model is shown on you
		boolean wantHide = CubecraftConfig.hideBody && wornReady && ModelRender.showing()
			&& !mc.options.getCameraType().isFirstPerson();
		if (wantHide) {
			mc.player.setInvisible(true);
			hidApplied = true;
		} else if (hidApplied) {
			if (mc.player.getEffect(MobEffects.INVISIBILITY) == null) mc.player.setInvisible(false);
			hidApplied = false;
		}

		ModelHologram.tick(mc);
	}

	public static void say(Minecraft mc, String text) {
		if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
	}

	private Models() {
	}
}
