package com.cubecraft;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class CubecraftClient implements ClientModInitializer {
	public static KeyMapping OPEN_MENU;
	public static KeyMapping ZOOM;
	public static KeyMapping REPEAT_EMOTE;

	@Override
	public void onInitializeClient() {
		CubecraftConfig.load();
		Models.rescan();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("cubecraft", "main"));
		OPEN_MENU = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.cubecraft.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		ZOOM = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.cubecraft.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
		REPEAT_EMOTE = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.cubecraft.repeat_emote", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (OPEN_MENU.consumeClick()) {
				if (mc.screen == null && mc.player != null) {
					mc.setScreen(new CubecraftScreen());
				}
			}
			while (REPEAT_EMOTE.consumeClick()) {
				if (mc.screen == null && mc.player != null) {
					Emotes.start(Emotes.last);
				}
			}
			Extras.tick(mc);
			SkyFx.tick(mc);
			Emotes.tick(mc);
			Models.tick(mc);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> emote = ClientCommandManager.literal("emote");
			for (Emote e : Emote.values()) {
				emote = emote.then(ClientCommandManager.literal(e.id).executes(ctx -> {
					Minecraft.getInstance().execute(() -> Emotes.start(e));
					return 1;
				}));
			}
			dispatcher.register(ClientCommandManager.literal("cubecraft")
				.executes(ctx -> {
					Minecraft mc = Minecraft.getInstance();
					// open on the next tick, otherwise the closing chat screen replaces it
					mc.execute(() -> mc.setScreen(new CubecraftScreen()));
					return 1;
				})
				.then(emote));
		});
	}
}
