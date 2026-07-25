package com.cosmicplayer.stafftracker;

import com.cosmicplayer.stafftracker.StaffTrackerConfig.CountKey;
import com.cosmicplayer.stafftracker.hud.CounterHud;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Watches the bound counter input each tick and increments the counter
 * once per press. Only fires during gameplay, never with a screen open.
 */
public final class CountKeyListener {
    private static boolean wasDown;

    private CountKeyListener() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean down = isPressed(client);
            if (down && !wasDown && client.currentScreen == null && client.player != null) {
                HelpData.get().increment();
                CounterHud.onIncrement(client);
            }
            wasDown = down;
        });
    }

    private static boolean isPressed(MinecraftClient client) {
        CountKey key = StaffTrackerConfig.get().countKey;
        long window = client.getWindow().getHandle();

        boolean baseDown = key.isMouse()
                ? GLFW.glfwGetMouseButton(window, key.code) == GLFW.GLFW_PRESS
                : GLFW.glfwGetKey(window, key.code) == GLFW.GLFW_PRESS;
        return baseDown && modifiersHeld(window, key);
    }

    private static boolean modifiersHeld(long window, CountKey key) {
        if (key.shift && !eitherDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            return false;
        }
        if (key.ctrl && !eitherDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            return false;
        }
        if (key.alt && !eitherDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) {
            return false;
        }
        return true;
    }

    private static boolean eitherDown(long window, int left, int right) {
        return GLFW.glfwGetKey(window, left) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, right) == GLFW.GLFW_PRESS;
    }
}
