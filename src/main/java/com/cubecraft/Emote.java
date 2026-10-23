package com.cubecraft;

public enum Emote {
	HEART_SKY("heartsky", "\u2665 Heart & Sky"),
	RAINBOW("rainbow", "Rainbow Spiral"),
	SAKURA("sakura", "Sakura Rain"),
	FIREWORKS("fireworks", "Fireworks"),
	FLAME("flame", "Flame Ring"),
	SNOW("snow", "Snowfall"),
	MUSIC("music", "Music Notes"),
	SPARKLE("sparkle", "Sparkle Aura");

	public final String id;
	public final String label;

	Emote(String id, String label) {
		this.id = id;
		this.label = label;
	}
}
