package com.scriptica.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

public final class ScripticaClientMod implements ClientModInitializer {
    public static final String MOD_ID = "scriptica";

    private static KeyBinding openKeybind;

    @Override
    public void onInitializeClient() {
        openKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.scriptica.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            KeyBinding.Category.MISC
        ));

        ScripticaLog.init();
        ScriptStorage.ensureDirs();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKeybind.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ScripticaScreen());
                }
            }

            ScriptRunner.instance().dispatchTick();
        });

        // Fires for incoming game messages (including chat). This powers onChat(...)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            ScriptRunner.instance().dispatchChat(message.getString());
        });

        ScripticaLog.info("Scriptica initialized. Scripts dir: " + getScriptsDir());
    }

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Path getScriptsDir() {
        return getConfigDir().resolve("scriptica").resolve("scripts");
    }

    public static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }
}

