package com.cubecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class CubecraftScreen extends Screen {
	private enum Tab {
		WORLD("World"), SKIN("Skins"), CUSTOM("Custom"), MODELS("Models"), SKY("Sky & Fog"), SPACE("Cosmos"), EMOTES("Emotes"), EXTRAS("Extras");
		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private record Label(String text, int x, int y, int color) {
	}

	private record Face(int x, int y, int idx) {
	}

	private record CustomFace(int x, int y, String name) {
	}

	private static final int ACCENT = 0xFF3AD1C9;
	private static final int VIOLET = 0xFF8F6BFF;
	private static final int GREEN = 0xFF8CFFC4;
	private static final int GREY = 0xFF8D9AC0;
	private static final int PW = 340;
	private static final int PH = 264;
	private static final int SKIN_COUNT = 18;
	private static final int CELL = 38;

	private static final String[][] TIMES = {
		{"Sunrise", "23000"}, {"Day", "1000"}, {"Noon", "6000"},
		{"Sunset", "12500"}, {"Night", "14000"}, {"Midnight", "18000"}
	};
	private static final String[] WEATHER = {"Server", "Clear", "Rain", "Storm"};
	// name, sky RGB, fog RGB, particle stars, aurora, cosmic skybox (0 = none, see Cosmos.NAMES)
	private static final Object[][] PRESETS = {
		{"Galaxy", 0x1B0B3A, 0x4B2A8F, false, false, 1}, {"Nebula", 0x2B0F54, 0xC2379D, false, false, 2},
		{"Aurora", 0x061B2E, 0x0E5A5A, false, true, 4}, {"Vapor", 0xFF71CE, 0x01CDFE, true, false, 0},
		{"Blood Moon", 0x2A0505, 0x7A1010, false, false, 3}, {"Starry", 0x0B1026, 0x1B2A55, false, false, 5},
		{"Deep Space", 0x02020A, 0x0A0A24, false, false, 5}, {"Sunset", 0xFF7F50, 0xFFB07A, false, false, 0},
		{"Nether", 0x5A0A0A, 0x8B1A1A, false, false, 0}, {"Ocean", 0x0A4F8F, 0x2A7FBF, false, false, 0},
		{"Candy", 0xFF9ED8, 0xFFD1F0, false, false, 0}, {"Default", -1, -1, false, false, 0}
	};

	private static Tab tab = Tab.WORLD;

	private int px, py, cx0, y0;
	private final List<Label> labels = new ArrayList<>();
	private final List<Face> faces = new ArrayList<>();
	private final List<CustomFace> customFaces = new ArrayList<>();
	private final Set<AbstractWidget> tabBtns = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<AbstractWidget> chosen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<AbstractWidget, float[]> hover = new IdentityHashMap<>();

	/** Implemented by the sliders so the custom painter can draw their fill. */
	private interface Frac {
		double frac();

		int color();
	}

	public CubecraftScreen() {
		super(Component.literal("CubeCraft Studio"));
	}

	/** One 0..255 channel of an RGB colour. */
	private static final class Channel extends AbstractSliderButton implements Frac {
		private final String name;
		private final int shift;
		private final IntSupplier get;
		private final IntConsumer set;

		Channel(int x, int y, int w, String name, int shift, IntSupplier get, IntConsumer set) {
			super(x, y, w, 20, Component.empty(), ((get.getAsInt() >> shift) & 0xFF) / 255.0);
			this.name = name;
			this.shift = shift;
			this.get = get;
			this.set = set;
			updateMessage();
		}

		@Override
		public double frac() {
			return this.value;
		}

		@Override
		public int color() {
			return shift == 16 ? 0xFFE05A5A : shift == 8 ? 0xFF52D17C : 0xFF5A8FE8;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(name + ": " + (int) Math.round(this.value * 255)));
		}

		@Override
		protected void applyValue() {
			int v = (int) Math.round(this.value * 255);
			int c = (get.getAsInt() & ~(0xFF << shift)) | (v << shift);
			set.accept(c & 0xFFFFFF);
			CubecraftConfig.save();
		}
	}

	private void refresh() {
		clearWidgets();
		init();
	}

	private static final class Percent extends AbstractSliderButton implements Frac {
		private final String name;
		private final IntConsumer set;

		Percent(int x, int y, int w, String name, int pct, IntConsumer set) {
			super(x, y, w, 20, Component.empty(), pct / 100.0);
			this.name = name;
			this.set = set;
			updateMessage();
		}

		@Override
		public double frac() {
			return this.value;
		}

		@Override
		public int color() {
			return VIOLET;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(name + ": " + (int) Math.round(this.value * 100) + "%"));
		}

		@Override
		protected void applyValue() {
			set.accept((int) Math.round(this.value * 100));
			CubecraftConfig.save();
		}
	}

	private Button btn(String text, int x, int y, int w, Runnable action) {
		Button b = Button.builder(Component.literal(text), x1 -> action.run()).bounds(x, y, w, 20).build();
		addRenderableWidget(b);
		return b;
	}

	private void label(String text, int x, int y, int color) {
		labels.add(new Label(text, x, y, color));
	}

	private static String onOff(String what, boolean on) {
		return what + ": " + (on ? "ON" : "OFF");
	}

	@Override
	protected void init() {
		labels.clear();
		faces.clear();
		customFaces.clear();
		tabBtns.clear();
		chosen.clear();
		hover.clear();
		px = (this.width - PW) / 2;
		py = Math.max(6, (this.height - PH) / 2);
		cx0 = px + 96;
		y0 = py + 34;

		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			Button b = btn(t.label, px + 10, y0 + i * 24, 74, () -> {
				tab = t;
				refresh();
			});
			b.active = t != tab;
			tabBtns.add(b);
			if (t == tab) chosen.add(b);
		}
		btn("Close", px + 10, py + PH - 28, 74, this::onClose);

		switch (tab) {
			case WORLD -> buildWorld();
			case SKIN -> buildSkin();
			case CUSTOM -> buildCustom();
			case MODELS -> buildModels();
			case SKY -> buildSky();
			case SPACE -> buildCosmos();
			case EMOTES -> buildEmotes();
			case EXTRAS -> buildExtras();
		}
	}

	private void buildWorld() {
		String timeName = "Off";
		for (String[] t : TIMES) {
			if (Integer.parseInt(t[1]) == CubecraftConfig.lockTime) timeName = t[0];
		}
		label("Time lock (client-side): " + timeName, cx0, y0, GREEN);
		for (int i = 0; i < TIMES.length; i++) {
			int value = Integer.parseInt(TIMES[i][1]);
			btn(TIMES[i][0], cx0 + (i % 3) * 79, y0 + 12 + (i / 3) * 24, 74, () -> {
				CubecraftConfig.lockTime = value;
				CubecraftConfig.save();
				refresh();
			});
		}
		btn("Unlock time", cx0, y0 + 62, 232, () -> {
			CubecraftConfig.lockTime = -1;
			CubecraftConfig.save();
			refresh();
		});
		label("Weather (client-side): " + WEATHER[CubecraftConfig.weather], cx0, y0 + 92, GREEN);
		for (int i = 0; i < WEATHER.length; i++) {
			int w = i;
			btn(WEATHER[i], cx0 + i * 59, y0 + 104, 55, () -> {
				CubecraftConfig.weather = w;
				CubecraftConfig.save();
				refresh();
			});
		}
		label("Visual only - no op or cheats needed.", cx0, y0 + 136, GREY);
		label("The server's real time and weather don't change.", cx0, y0 + 148, GREY);
	}

	private void buildSkin() {
		label(CubecraftConfig.skin < 0 || !CubecraftConfig.customSkin.isEmpty() ? (CubecraftConfig.customSkin.isEmpty() ? "Using your real skin" : "Using a custom skin") : "Selected: skin #" + (CubecraftConfig.skin + 1), cx0, y0, GREEN);
		for (int i = 0; i < SKIN_COUNT; i++) {
			final int idx = i;
			int x = cx0 + (i % 6) * CELL;
			int y = y0 + 14 + (i / 6) * CELL;
			Button b = Button.builder(Component.empty(), x1 -> {
				CubecraftConfig.skin = idx;
				CubecraftConfig.customSkin = "";
				CubecraftConfig.save();
				refresh();
			}).bounds(x, y, CELL - 2, CELL - 2).build();
			addRenderableWidget(b);
			faces.add(new Face(x, y, i));
		}
		int by = y0 + 14 + 3 * CELL + 4;
		btn("My real skin", cx0, by, 112, () -> {
			CubecraftConfig.skin = -1;
			CubecraftConfig.customSkin = "";
			CubecraftConfig.save();
			refresh();
		});
		btn("Random", cx0 + 120, by, 112, () -> {
			CubecraftConfig.skin = ThreadLocalRandom.current().nextInt(SKIN_COUNT);
			CubecraftConfig.customSkin = "";
			CubecraftConfig.save();
			refresh();
		});
		label("Only you see skin changes (client-side).", cx0, by + 26, GREY);
	}

	private static final int CUSTOM_PER_PAGE = 12;
	private static int customPage = 0;

	private void buildCustom() {
		CustomSkins.reload();
		java.util.List<String> all = CustomSkins.names();
		int pages = Math.max(1, (all.size() + CUSTOM_PER_PAGE - 1) / CUSTOM_PER_PAGE);
		customPage = Math.max(0, Math.min(pages - 1, customPage));
		label(CubecraftConfig.customSkin.isEmpty() ? "Pick a skin from your folder" : "Selected: " + CubecraftConfig.customSkin, cx0, y0, GREEN);
		for (int i = 0; i < CUSTOM_PER_PAGE; i++) {
			int n = customPage * CUSTOM_PER_PAGE + i;
			if (n >= all.size()) break;
			final String name = all.get(n);
			int x = cx0 + (i % 6) * CELL;
			int y = y0 + 14 + (i / 6) * CELL;
			Button b = Button.builder(Component.empty(), x1 -> {
				CubecraftConfig.customSkin = name;
				CubecraftConfig.save();
				refresh();
			}).bounds(x, y, CELL - 2, CELL - 2).build();
			b.setTooltip(Tooltip.create(Component.literal(name)));
			addRenderableWidget(b);
			customFaces.add(new CustomFace(x, y, name));
		}
		int by = y0 + 14 + 2 * CELL + 4;
		Button prev = btn("<", cx0, by, 24, () -> {
			customPage--;
			refresh();
		});
		prev.active = customPage > 0;
		label("Page " + (customPage + 1) + "/" + pages, cx0 + 32, by + 6, GREY);
		Button next = btn(">", cx0 + 88, by, 24, () -> {
			customPage++;
			refresh();
		});
		next.active = customPage < pages - 1;
		btn("Reload", cx0 + 120, by, 56, () -> refresh());
		btn("Use my skin", cx0 + 180, by, 76, () -> {
			CubecraftConfig.customSkin = "";
			CubecraftConfig.skin = -1;
			CubecraftConfig.save();
			refresh();
		});
		label(all.isEmpty() ? "No skins found. Put 64x64 .png files in:" : "Add more 64x64 .png files in:", cx0, by + 28, GREY);
		label("config/cubecraft/skins  (name_slim.png = slim arms)", cx0, by + 40, GREY);
	}

	private static final int MODELS_PER_PAGE = 6;
	private static final String[] RENDER_MODES = {"Auto", "Solid A", "Solid B", "Solid C", "Hologram"};
	private static int modelPage = 0;
	/** One-line message from the last Place click, shown where the selection is. Cleared on the next rebuild. */
	private String note = "";

	/** Size of the selected model, 0.1x to 5x on a log scale so small changes are easy near 1x. */
	private static final class SizeSlider extends AbstractSliderButton implements Frac {
		private final String file;

		SizeSlider(int x, int y, int w, String file) {
			super(x, y, w, 20, Component.empty(), toSlider(Models.pref(file).scale));
			this.file = file;
			updateMessage();
		}

		static double toSlider(float s) {
			return Math.max(0.0, Math.min(1.0, Math.log(s / 0.1) / Math.log(50)));
		}

		static float fromSlider(double v) {
			float s = (float) (0.1 * Math.pow(50, v));
			return Math.abs(s - 1f) < 0.04f ? 1f : s;
		}

		@Override
		public double frac() {
			return this.value;
		}

		@Override
		public int color() {
			return ACCENT;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal("Size: " + String.format(java.util.Locale.ROOT, "%.2f", fromSlider(this.value)) + "x"));
		}

		@Override
		protected void applyValue() {
			Models.pref(file).scale = fromSlider(this.value);
			CubecraftConfig.save();
		}
	}

	private String clip(String text, int width) {
		if (this.font.width(text) <= width) return text;
		return this.font.plainSubstrByWidth(text, width - this.font.width("..")) + "..";
	}

	private void buildModels() {
		note = "";
		List<String> all = Models.names();
		int pages = Math.max(1, (all.size() + MODELS_PER_PAGE - 1) / MODELS_PER_PAGE);
		modelPage = Math.max(0, Math.min(pages - 1, modelPage));
		String file = CubecraftConfig.modelFile;
		boolean have = !file.isEmpty();

		if (all.isEmpty()) {
			label("No models found. Put .glb / .gltf / .obj files in:", cx0, y0 + 16, GREY);
			label("config/cubecraft/models  (then press Rescan)", cx0, y0 + 28, GREY);
		}
		for (int i = 0; i < MODELS_PER_PAGE; i++) {
			int n = modelPage * MODELS_PER_PAGE + i;
			if (n >= all.size()) break;
			final String name = all.get(n);
			int x = cx0 + (i % 2) * 118;
			int y = y0 + 12 + (i / 2) * 22;
			Button b = btn(clip(name, 104), x, y, 114, () -> {
				CubecraftConfig.modelFile = name;
				Models.get(name); // start loading right away
				CubecraftConfig.save();
				refresh();
			});
			b.active = !name.equals(file);
			if (name.equals(file)) chosen.add(b);
			b.setTooltip(Tooltip.create(Component.literal(name)));
		}

		int ny = y0 + 80;
		Button prev = btn("<", cx0, ny, 24, () -> {
			modelPage--;
			refresh();
		});
		prev.active = modelPage > 0;
		label("Page " + (modelPage + 1) + "/" + pages, cx0 + 32, ny + 6, GREY);
		Button next = btn(">", cx0 + 88, ny, 24, () -> {
			modelPage++;
			refresh();
		});
		next.active = modelPage < pages - 1;
		Button rescan = btn("Rescan", cx0 + 120, ny, 56, () -> {
			Models.rescan();
			Models.dropCache(); // edited files are read again
			ModelRender.retry();
			refresh();
		});
		rescan.setTooltip(Tooltip.create(Component.literal("Re-read the folder and reload every model")));
		Button clear = btn("Clear all", cx0 + 180, ny, 52, () -> {
			Models.PROPS.clear();
			refresh();
		});
		clear.setTooltip(Tooltip.create(Component.literal("Remove every model you placed in the world")));

		int ay = y0 + 104;
		Button place = btn("Place in front", cx0, ay, 112, () -> {
			Minecraft mc = Minecraft.getInstance();
			note = Models.place(mc);
			if (note.startsWith("Placed")) {
				Models.say(mc, note);
				onClose();
			}
		});
		place.active = have;
		place.setTooltip(Tooltip.create(Component.literal("Stands the model 2.5 blocks in front of you, facing you")));
		Button wear = btn(onOff("Wear", CubecraftConfig.modelWear), cx0 + 120, ay, 112, () -> {
			CubecraftConfig.modelWear = !CubecraftConfig.modelWear;
			CubecraftConfig.save();
			refresh();
		});
		wear.active = have;
		wear.setTooltip(Tooltip.create(Component.literal("Show the model on you. Third person only (F5)")));

		int by = y0 + 128;
		Button hide = btn(onOff("Hide body", CubecraftConfig.hideBody), cx0, by, 112, () -> {
			CubecraftConfig.hideBody = !CubecraftConfig.hideBody;
			CubecraftConfig.save();
			refresh();
		});
		hide.setTooltip(Tooltip.create(Component.literal("Make your own body invisible while the model is worn")));
		Button draw = btn("Draw: " + RENDER_MODES[CubecraftConfig.modelRender], cx0 + 120, by, 112, () -> {
			CubecraftConfig.modelRender = (CubecraftConfig.modelRender + 1) % RENDER_MODES.length;
			CubecraftConfig.save();
			ModelRender.retry();
			refresh();
		});
		draw.setTooltip(Tooltip.create(Component.literal("Auto, Solid A/B/C (real 3D, different render hooks) or Hologram (particles). If nothing shows, try the next one.")));

		int sy = y0 + 152;
		if (have) {
			addRenderableWidget(new SizeSlider(cx0, sy, 232, file));
		} else {
			btn("Size: pick a model first", cx0, sy, 232, () -> {
			}).active = false;
		}

		int ty = y0 + 176;
		Models.Pref pr = have ? Models.pref(file) : new Models.Pref();
		Button turn = btn("Turn: " + (pr.turn * 90) + "\u00b0", cx0, ty, 74, () -> {
			Models.pref(CubecraftConfig.modelFile).turn = (Models.pref(CubecraftConfig.modelFile).turn + 1) % 4;
			CubecraftConfig.save();
			refresh();
		});
		turn.active = have;
		turn.setTooltip(Tooltip.create(Component.literal("Quarter turn. Applies to the worn model and to the next one you place")));
		Button zup = btn(onOff("Z-up", pr.zup), cx0 + 79, ty, 74, () -> {
			Models.Pref p = Models.pref(CubecraftConfig.modelFile);
			p.zup = !p.zup;
			CubecraftConfig.save();
			refresh();
		});
		zup.active = have;
		zup.setTooltip(Tooltip.create(Component.literal("Turn on if the model lies on its back (Blender / CAD files use Z as up)")));
		Button undo = btn("Remove last", cx0 + 158, ty, 74, () -> {
			if (!Models.PROPS.isEmpty()) Models.PROPS.remove(Models.PROPS.size() - 1);
			refresh();
		});
		undo.active = !Models.PROPS.isEmpty();
	}

	private void addRgb(int x, int y, IntSupplier get, IntConsumer set) {
		String[] n = {"R", "G", "B"};
		int[] shifts = {16, 8, 0};
		for (int i = 0; i < 3; i++) {
			addRenderableWidget(new Channel(x, y + i * 22, 112, n[i], shifts[i], get, set));
		}
	}

	private void buildSky() {
		int right = cx0 + 120;
		Button sky = btn(onOff("Sky", CubecraftConfig.skyOn), cx0, y0, 112, () -> {
			CubecraftConfig.skyOn = !CubecraftConfig.skyOn;
			CubecraftConfig.save();
			refresh();
		});
		sky.setTooltip(Tooltip.create(Component.literal("Tint the sky colour")));
		Button fog = btn(onOff("Fog", CubecraftConfig.fogOn), right, y0, 112, () -> {
			CubecraftConfig.fogOn = !CubecraftConfig.fogOn;
			CubecraftConfig.save();
			refresh();
		});
		fog.setTooltip(Tooltip.create(Component.literal("Tint the fog and horizon colour")));
		addRgb(cx0, y0 + 24, () -> CubecraftConfig.skyColor, c -> {
			CubecraftConfig.skyColor = c;
			CubecraftConfig.skyOn = true;
		});
		addRgb(right, y0 + 24, () -> CubecraftConfig.fogColor, c -> {
			CubecraftConfig.fogColor = c;
			CubecraftConfig.fogOn = true;
		});
		label("Presets", cx0, y0 + 108, GREEN);
		for (int i = 0; i < PRESETS.length; i++) {
			final Object[] pr = PRESETS[i];
			btn((String) pr[0], cx0 + (i % 4) * 59, y0 + 120 + (i / 4) * 24, 55, () -> {
				int sk = (Integer) pr[1];
				int fg = (Integer) pr[2];
				int cos = (Integer) pr[5];
				if (sk < 0) {
					CubecraftConfig.skyOn = false;
					CubecraftConfig.fogOn = false;
					CubecraftConfig.stars = false;
					CubecraftConfig.aurora = false;
					CubecraftConfig.shooting = false;
					CubecraftConfig.cosmos = 0;
				} else {
					CubecraftConfig.skyColor = sk;
					CubecraftConfig.fogColor = fg;
					CubecraftConfig.skyOn = cos == 0;
					CubecraftConfig.fogOn = cos == 0; // cosmic skies pick their own horizon fog
					CubecraftConfig.stars = (Boolean) pr[3];
					CubecraftConfig.aurora = (Boolean) pr[4];
					CubecraftConfig.cosmos = cos;
					CubecraftConfig.shooting = cos > 0 || (Boolean) pr[3];
					if (cos > 0 && CubecraftConfig.lockTime < 0) CubecraftConfig.lockTime = 18000;
				}
				CubecraftConfig.save();
				refresh();
			});
		}
	}

	private void buildCosmos() {
		label("Cosmic sky - real skybox, only you see it", cx0, y0, GREEN);
		for (int i = 0; i < Cosmos.NAMES.length; i++) {
			final int idx = i;
			Button b = btn(Cosmos.NAMES[i], cx0 + (i % 3) * 79, y0 + 12 + (i / 3) * 24, 74, () -> {
				CubecraftConfig.cosmos = idx;
				if (idx > 0 && CubecraftConfig.lockTime < 0) CubecraftConfig.lockTime = 18000; // sky shows best at night
				CubecraftConfig.save();
				refresh();
			});
			b.active = idx != CubecraftConfig.cosmos;
			if (idx == CubecraftConfig.cosmos) chosen.add(b);
		}
		btn(onOff("Aurora", CubecraftConfig.aurora), cx0, y0 + 64, 112, () -> {
			CubecraftConfig.aurora = !CubecraftConfig.aurora;
			CubecraftConfig.save();
			refresh();
		});
		btn(onOff("Meteors", CubecraftConfig.shooting), cx0 + 120, y0 + 64, 112, () -> {
			CubecraftConfig.shooting = !CubecraftConfig.shooting;
			CubecraftConfig.save();
			refresh();
		});
		addRenderableWidget(new Percent(cx0, y0 + 88, 232, "Brightness", CubecraftConfig.cosmosBright,
			v -> CubecraftConfig.cosmosBright = v));
		btn(onOff("Slow spin", CubecraftConfig.cosmosSpin), cx0, y0 + 112, 112, () -> {
			CubecraftConfig.cosmosSpin = !CubecraftConfig.cosmosSpin;
			CubecraftConfig.save();
			refresh();
		});
		Button night = btn("Set night", cx0 + 120, y0 + 112, 112, () -> {
			CubecraftConfig.lockTime = 18000;
			CubecraftConfig.save();
		});
		night.setTooltip(Tooltip.create(Component.literal("Locks your client time to midnight so terrain matches the sky")));
		btn(onOff("Old particle sky", CubecraftConfig.stars), cx0, y0 + 136, 232, () -> {
			CubecraftConfig.stars = !CubecraftConfig.stars;
			CubecraftConfig.save();
			refresh();
		});
		label(Cosmos.status(), cx0, y0 + 164, CubecraftConfig.cosmos > 0 ? GREEN : GREY);
		label("Overworld only. Fullbright (Extras) lights the terrain.", cx0, y0 + 176, GREY);
	}

	private void buildEmotes() {
		label("Click to play. Only you see emotes.", cx0, y0, GREEN);
		Emote[] all = Emote.values();
		for (int i = 0; i < all.length; i++) {
			Emote e = all[i];
			btn(e.label, cx0 + (i % 2) * 118, y0 + 14 + (i / 2) * 26, 114, () -> {
				onClose();
				Emotes.start(e);
			});
		}
		label("Press G to repeat the last emote.", cx0, y0 + 124, GREY);
		label("Or use /cubecraft emote <name>", cx0, y0 + 136, GREY);
	}

	private void buildExtras() {
		label("Quality of life", cx0, y0, GREEN);
		Button fb = btn(onOff("Fullbright (night vision)", CubecraftConfig.fullbright), cx0, y0 + 14, 232, () -> {
			CubecraftConfig.fullbright = !CubecraftConfig.fullbright;
			CubecraftConfig.save();
			refresh();
		});
		fb.setTooltip(Tooltip.create(Component.literal("Client-side night vision, no potion needed")));
		label("Hold C to zoom (rebind in Controls).", cx0, y0 + 44, GREY);
		label("Right Shift opens this menu.", cx0, y0 + 56, GREY);
		btn("Reset all settings", cx0, y0 + 78, 232, () -> {
			CubecraftConfig.resetAll();
			refresh();
		});
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(null);
	}

	// ---------------------------------------------------------------- look & feel

	private static int argb(int a, int rgb) {
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private static int mix(int c1, int c2, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int a = (int) (((c1 >>> 24) & 0xFF) * (1 - t) + ((c2 >>> 24) & 0xFF) * t);
		int r = (int) (((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
		int g = (int) (((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
		int b = (int) ((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Filled rectangle with the four corner pixels cut off. */
	private static void rrect(GuiGraphics g, int x, int y, int w, int h, int c) {
		g.fill(x + 1, y, x + w - 1, y + h, c);
		g.fill(x, y + 1, x + w, y + h - 1, c);
	}

	/** Rounded border with a vertical gradient inside. */
	private static void pill(GuiGraphics g, int x, int y, int w, int h, int border, int top, int bottom) {
		rrect(g, x, y, w, h, border);
		g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bottom);
	}

	private void text(GuiGraphics g, String s, int x, int y, int color) {
		g.drawString(this.font, Component.literal(s), x, y, color);
	}

	private void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
		text(g, s, cx - this.font.width(s) / 2, y, color);
	}

	private static int tabColor(int i) {
		int[] c = {0xFF5AB8FF, 0xFFFFB45A, 0xFFB08CFF, 0xFF6BE0A0, 0xFFFF7AA8, 0xFF9A7CFF, 0xFFFFE066, 0xFF8FA0C8};
		return c[Math.floorMod(i, c.length)];
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.renderBackground(g, mouseX, mouseY, delta);
		double t = System.nanoTime() / 1.0e9;

		// soft drop shadow
		for (int k = 10; k >= 1; k--) {
			g.fill(px - k, py - k, px + PW + k, py + PH + k, argb(10, 0x000000));
		}
		// frame + body
		g.fillGradient(px - 1, py - 1, px + PW + 1, py + PH + 1, ACCENT, VIOLET);
		g.fillGradient(px, py, px + PW, py + PH, 0xFF0B1022, 0xFF182248);

		// twinkling stars in the panel
		int seed = 12345;
		for (int i = 0; i < 46; i++) {
			seed = seed * 1103515245 + 12345;
			int sx = px + 2 + ((seed >>> 8) & 0x7FFF) % (PW - 4);
			seed = seed * 1103515245 + 12345;
			int sy = py + 2 + ((seed >>> 8) & 0x7FFF) % (PH - 4);
			double tw = 0.5 + 0.5 * Math.sin(t * (0.8 + (i % 5) * 0.35) + i * 1.7);
			int a = (int) (30 + 150 * tw);
			g.fill(sx, sy, sx + 1, sy + 1, argb(a, i % 7 == 0 ? 0xFFD9A0 : 0xCFE2FF));
		}

		// header
		g.fillGradient(px, py, px + PW, py + 26, 0xE0182458, 0xE00E1636);
		g.fill(px, py + 25, px + PW, py + 26, 0xFF2B3C72);
		double sh = (t * 70) % (PW + 120) - 60;
		for (int k = 0; k < 60; k += 2) {
			double d = 1.0 - Math.abs(k - 30) / 30.0;
			int sx = (int) (px + sh + k);
			if (sx < px || sx + 2 > px + PW) continue;
			g.fill(sx, py + 24, sx + 2, py + 27, argb((int) (230 * d * d), 0x7CF3EC));
		}
		// little cube logo
		g.fill(px + 10, py + 6, px + 21, py + 9, 0xFF9AF7F1);
		g.fill(px + 10, py + 9, px + 15, py + 18, ACCENT);
		g.fill(px + 15, py + 9, px + 21, py + 18, 0xFF1D8782);
		g.fill(px + 15, py + 9, px + 16, py + 18, 0xFF0F4F4C);
		text(g, "CubeCraft", px + 27, py + 9, 0xFFFFFFFF);
		text(g, "Studio", px + 27 + this.font.width("CubeCraft "), py + 9, ACCENT);
		int chipW = this.font.width("v0.8") + 10;
		pill(g, px + PW - chipW - 8, py + 6, chipW, 14, 0xFF34447A, 0xFF1F2B58, 0xFF172046);
		textCentered(g, "v0.8", px + PW - chipW / 2 - 8, py + 9, GREY);

		// sidebar
		pill(g, px + 6, py + 30, 80, PH - 36, 0x2A8FA0D0, 0x66050816, 0x66080C1E);
		// content card
		pill(g, px + 90, py + 30, PW - 96, PH - 36, 0xFF253158, 0xE60E1632, 0xE6090E1E);

		for (Label l : labels) {
			if (l.color() == GREEN) {
				g.fill(l.x() - 4, l.y() + 1, l.x() - 2, l.y() + 8, ACCENT);
			}
			text(g, l.text(), l.x(), l.y(), l.color());
		}
	}

	private static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	/** Paints over the vanilla button and slider textures with the CubeCraft look. */
	private void paintWidgets(GuiGraphics g, int mouseX, int mouseY) {
		for (GuiEventListener c : this.children()) {
			if (!(c instanceof AbstractWidget w) || !w.visible) continue;
			int x = w.getX(), y = w.getY(), wd = w.getWidth(), h = w.getHeight();
			float[] hv = hover.computeIfAbsent(w, k -> new float[1]);
			boolean over = w.active && mouseX >= x && mouseY >= y && mouseX < x + wd && mouseY < y + h;
			hv[0] += ((over ? 1f : 0f) - hv[0]) * 0.35f;
			float hh = hv[0];
			String msg = w.getMessage().getString();

			g.fill(x, y, x + wd, y + h, 0xFF0A0F1F);

			if (w instanceof Frac fr) {
				pill(g, x, y, wd, h, mix(0xFF34447A, ACCENT, hh), 0xFF111830, 0xFF0C1124);
				int fw = (int) Math.round((wd - 2) * Math.max(0, Math.min(1, fr.frac())));
				if (fw > 0) {
					g.fillGradient(x + 1, y + 1, x + 1 + fw, y + h - 1, mix(fr.color(), 0xFFFFFFFF, 0.25f), mix(fr.color(), 0xFF000000, 0.35f));
				}
				int kx = x + 1 + (int) Math.round((wd - 6) * Math.max(0, Math.min(1, fr.frac())));
				g.fill(kx, y - 1, kx + 4, y + h + 1, mix(0xFFDCE6FF, 0xFFFFFFFF, hh));
				g.fill(kx + 1, y, kx + 3, y + h, mix(0xFF8FA0D0, fr.color(), 0.5f));
				textCentered(g, msg, x + wd / 2, y + (h - 8) / 2, 0xFFFFFFFF);
				continue;
			}

			boolean sel = chosen.contains(w);
			boolean danger = msg.equals("Reset all settings") || msg.equals("Clear all");
			int border, top, bottom, fg;
			if (msg.isEmpty()) { // skin cell
				border = mix(0xFF2A3560, ACCENT, hh);
				top = mix(0xFF1A2342, 0xFF263459, hh);
				bottom = mix(0xFF111833, 0xFF1A2444, hh);
				fg = 0xFFFFFFFF;
			} else if (sel) {
				border = 0xFF8CF6F0;
				top = 0xFF48E0D8;
				bottom = 0xFF2BA9A3;
				fg = 0xFF03171B;
			} else if (!w.active) {
				border = 0xFF1C2542;
				top = 0xFF121930;
				bottom = 0xFF0F1528;
				fg = 0xFF566080;
			} else if (danger) {
				border = mix(0xFF5C2B3A, 0xFFE0606F, hh);
				top = mix(0xFF2D1B2B, 0xFF52263A, hh);
				bottom = mix(0xFF22141F, 0xFF3C1B2B, hh);
				fg = mix(0xFFFF9AA8, 0xFFFFD6DB, hh);
			} else {
				border = mix(0xFF33437A, ACCENT, hh);
				top = mix(0xFF26345F, 0xFF36497F, hh);
				bottom = mix(0xFF1B2547, 0xFF263664, hh);
				fg = mix(0xFFD2DAF2, 0xFFFFFFFF, hh);
			}
			pill(g, x, y, wd, h, border, top, bottom);
			if (!msg.isEmpty() && w.active && !sel) {
				g.fill(x + 2, y + 1, x + wd - 2, y + 2, argb(26 + (int) (30 * hh), 0xFFFFFF)); // top sheen
			}

			int ty = y + (h - 8) / 2;
			if (msg.isEmpty()) continue;
			if (tabBtns.contains(w)) {
				int idx = 0;
				for (Tab tb : Tab.values()) {
					if (tb.label.equals(msg)) idx = tb.ordinal();
				}
				int ic = sel ? 0xFF03171B : tabColor(idx);
				g.fill(x + 7, y + 6, x + 13, y + h - 6, ic);
				g.fill(x + 7, y + 6, x + 13, y + 7, argb(120, 0xFFFFFF));
				text(g, msg, x + 18, ty, fg);
			} else if (msg.endsWith(": ON") || msg.endsWith(": OFF")) {
				boolean on = msg.endsWith(": ON");
				String name = msg.substring(0, msg.lastIndexOf(':'));
				text(g, clip(name, wd - 34), x + 7, ty, fg);
				int tx = x + wd - 25, tyy = y + (h - 10) / 2;
				pill(g, tx, tyy, 19, 10, on ? 0xFF7CF3C0 : 0xFF3A4670, on ? 0xFF3EDB95 : 0xFF222B4A, on ? 0xFF22A86E : 0xFF1A2240);
				int kx = on ? tx + 11 : tx + 2;
				g.fill(kx, tyy + 2, kx + 6, tyy + 8, on ? 0xFFFFFFFF : 0xFF8D9AC0);
			} else {
				textCentered(g, clip(msg, wd - 8), x + wd / 2, ty, fg);
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		paintWidgets(g, mouseX, mouseY);
		for (Face f : faces) {
			PlayerFaceRenderer.draw(g, DefaultPlayerSkin.get(new UUID(0L, f.idx())), f.x() + 4, f.y() + 4, CELL - 10);
			if (f.idx() == CubecraftConfig.skin) {
				box(g, f.x(), f.y(), CELL - 2, CELL - 2, GREEN);
				box(g, f.x() + 1, f.y() + 1, CELL - 4, CELL - 4, argb(120, 0x8CFFC4));
			}
		}
		for (CustomFace f : customFaces) {
			net.minecraft.world.entity.player.PlayerSkin sk = CustomSkins.get(f.name());
			if (sk != null) {
				PlayerFaceRenderer.draw(g, sk, f.x() + 4, f.y() + 4, CELL - 10);
			}
			if (f.name().equals(CubecraftConfig.customSkin)) {
				box(g, f.x(), f.y(), CELL - 2, CELL - 2, GREEN);
				box(g, f.x() + 1, f.y() + 1, CELL - 4, CELL - 4, argb(120, 0x8CFFC4));
			}
		}
		if (tab == Tab.MODELS) {
			String f = CubecraftConfig.modelFile;
			String line;
			int color = GREEN;
			if (!note.isEmpty()) {
				line = note;
			} else if (f.isEmpty()) {
				line = "Pick a model from your folder";
			} else {
				Models.Entry e = Models.get(f);
				if (e.state == Models.LOADING) {
					line = "Loading " + f + "...";
					color = GREY;
				} else if (e.state == Models.READY) {
					line = "Selected: " + f + " (" + e.data.triangles + " triangles)";
				} else {
					line = "Could not load: " + e.error;
					color = 0xFFFF8A80;
				}
			}
			g.drawString(this.font, Component.literal(clip(line, 232)), cx0, y0, color);
			String[] st = ModelRender.status();
			g.drawString(this.font, Component.literal(clip(st[0], 232)), cx0, y0 + 201, GREEN);
			g.drawString(this.font, Component.literal(clip(st[1], 232)), cx0, y0 + 212, GREY);
		}
		if (tab == Tab.SKY) {
			int right = cx0 + 120;
			rrect(g, cx0, y0 + 92, 112, 12, 0xFFC8D4F5);
			g.fill(cx0 + 1, y0 + 93, cx0 + 111, y0 + 103, 0xFF000000 | CubecraftConfig.skyColor);
			rrect(g, right, y0 + 92, 112, 12, 0xFFC8D4F5);
			g.fill(right + 1, y0 + 93, right + 111, y0 + 103, 0xFF000000 | CubecraftConfig.fogColor);
		}
	}
}
