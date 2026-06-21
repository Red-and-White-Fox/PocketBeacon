package com.redandwhitefox.pocketbeacon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import com.redandwhitefox.pocketbeacon.PocketBeacon;
import com.redandwhitefox.pocketbeacon.client.gui.BeaconTrinketScreen;
import com.redandwhitefox.pocketbeacon.network.OpenBeaconTrinketPayload;

public class PocketBeaconClient implements ClientModInitializer {

	public static KeyBinding openTrinketGuiKey;

	@Override
	public void onInitializeClient() {
		HandledScreens.register(PocketBeacon.BEACON_TRINKET_SCREEN_HANDLER, BeaconTrinketScreen::new);

		openTrinketGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.pocketbeacon.open_gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				"category.pocketbeacon"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openTrinketGuiKey.wasPressed()) {
				// Use the record that is actually registered on the server
				ClientPlayNetworking.send(new OpenBeaconTrinketPayload());
			}
		});
	}


}
