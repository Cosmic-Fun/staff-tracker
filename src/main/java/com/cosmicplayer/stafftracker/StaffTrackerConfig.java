package com.cosmicplayer.stafftracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User settings. Saved to config/stafftracker/settings.json.
 * The HUD position is stored as a fraction of the free screen space,
 * so it stays on screen at any resolution or GUI scale.
 */
public final class StaffTrackerConfig {
    /** Which period the HUD counter displays. */
    public enum HudView {
        DAY("Day", "TODAY"),
        WEEK("Week", "THIS WEEK"),
        MONTH("Month", "THIS MONTH");

        public final String option;
        public final String hudLabel;

        HudView(String option, String hudLabel) {
            this.option = option;
            this.hudLabel = hudLabel;
        }
    }

    /**
     * The bound counter input. A custom binding rather than a vanilla
     * KeyBinding, so mouse buttons and modifier combos like Ctrl Shift H work.
     */
    public static class CountKey {
        public static final String KEYBOARD = "keyboard";
        public static final String MOUSE = "mouse";

        public String device = KEYBOARD;
        public int code = GLFW.GLFW_KEY_H;
        public boolean shift;
        public boolean ctrl;
        public boolean alt;

        public boolean isMouse() {
            return MOUSE.equals(device);
        }
    }

    public static final float DEFAULT_HUD_X = 0.02f;
    public static final float DEFAULT_HUD_Y = 0.05f;
    public static final float DEFAULT_HUD_SCALE = 0.75f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static StaffTrackerConfig instance = new StaffTrackerConfig();

    public boolean hudEnabled = true;
    public boolean showLabel = true;
    public HudView hudView = HudView.DAY;
    public CountKey countKey = new CountKey();
    public float hudX = DEFAULT_HUD_X;
    public float hudY = DEFAULT_HUD_Y;
    public float hudScale = DEFAULT_HUD_SCALE;

    public static StaffTrackerConfig get() {
        return instance;
    }

    public static void load() {
        Path file = path();
        if (Files.exists(file)) {
            try {
                instance = GSON.fromJson(Files.readString(file), StaffTrackerConfig.class);
            } catch (Exception e) {
                instance = new StaffTrackerConfig();
            }
        }
        if (instance == null) {
            instance = new StaffTrackerConfig();
        }
        instance.clamp();
    }

    public static void save() {
        try {
            JsonFiles.write(path(), GSON.toJson(instance));
        } catch (Exception e) {
            // A failed settings write is not worth crashing the game over.
        }
    }

    private void clamp() {
        if (hudView == null) {
            hudView = HudView.DAY;
        }
        if (countKey == null || countKey.device == null) {
            countKey = new CountKey();
        }
        hudX = MathHelper.clamp(hudX, 0.0f, 1.0f);
        hudY = MathHelper.clamp(hudY, 0.0f, 1.0f);
        hudScale = MathHelper.clamp(hudScale, 0.5f, 2.0f);
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(StaffTrackerClient.MOD_ID).resolve("settings.json");
    }
}
