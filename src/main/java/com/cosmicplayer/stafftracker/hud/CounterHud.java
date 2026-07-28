package com.cosmicplayer.stafftracker.hud;

import com.cosmicplayer.stafftracker.HelpData;
import com.cosmicplayer.stafftracker.StaffTrackerClient;
import com.cosmicplayer.stafftracker.StaffTrackerConfig;
import com.cosmicplayer.stafftracker.ui.Format;
import com.cosmicplayer.stafftracker.ui.TextPainter;
import com.cosmicplayer.stafftracker.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

/**
 * The on screen counter. A translucent rounded panel showing the total
 * for the period picked in settings, with an optional label above it.
 * Might just remove the label later.
 *
 * TextPainter draws any size sharp, so the HUD size setting just scales
 * the font sizes and paddings. Nothing gets stretched.
 */
public final class CounterHud {
    private static final int BACKGROUND = 0x8C090909;
    private static final int BORDER = 0x3CFFFFFF;
    private static final int DIVIDER = 0x2EFFFFFF;
    private static final int FLASH_MS = 400;

    // Base sizes at 100%. Everything scales from these.
    private static final float BASE_LABEL_SIZE = 8.0f;
    private static final float BASE_COUNT_SIZE = 20.0f;
    private static final int BASE_PADDING = 7;
    private static final int BASE_RADIUS = 6;
    private static final int BASE_MIN_WIDTH = 46;

    private static long flashStart = -1;

    /** Set by the position editor so the real HUD does not draw under the preview. */
    public static boolean suppressed = false;

    private CounterHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                StaffTrackerClient.id("counter"),
                CounterHud::render);
    }

    /** Called after each count. Flashes the HUD, or shows an action bar note when the HUD is off. */
    public static void onIncrement(MinecraftClient client) {
        flashStart = Util.getMeasuringTimeMs();
        if (!StaffTrackerConfig.get().hudEnabled && client.player != null) {
            Text message = Text.literal("Helped +1  ·  " + Format.count(HelpData.get().today()) + " today");
            client.player.sendMessage(message, true);
        }
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        StaffTrackerConfig config = StaffTrackerConfig.get();
        if (!config.hudEnabled || suppressed || client.options.hudHidden) {
            return;
        }
        renderPanel(context, client, true);
    }

    /** Draws the counter panel. Shared with the position editor preview. */
    public static void renderPanel(DrawContext context, MinecraftClient client, boolean allowFlash) {
        int[] size = panelSize();
        int[] position = panelPosition(client, size);

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(position[0], position[1]);
        drawPanel(context, size[0], size[1], allowFlash);
        matrices.popMatrix();
    }

    /** Panel bounds in screen pixels: x, y, width, height. Used for dragging. */
    public static float[] panelBounds(MinecraftClient client) {
        int[] size = panelSize();
        int[] position = panelPosition(client, size);
        return new float[]{position[0], position[1], size[0], size[1]};
    }

    private static void drawPanel(DrawContext context, int width, int height, boolean allowFlash) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        int padding = scaled(BASE_PADDING, 3);
        int radius = scaled(BASE_RADIUS, 3);
        Theme.roundedRect(context, 0, 0, width, height, radius, BACKGROUND);
        Theme.roundedRectOutline(context, 0, 0, width, height, radius, BORDER);

        int y = padding;
        if (config.showLabel) {
            TextPainter.draw(context, config.hudView.hudLabel, padding, y, labelSize(), Theme.TEXT_DIM, true);
            y += labelCapHeight() + scaled(3, 2);
            context.fill(padding, y, width - padding, y + 1, DIVIDER);
            y += 1 + scaled(5, 3);
        }

        // The count always centers, with or without the label. W Rtzy suggestion.
        String count = Format.count(HelpData.get().countFor(config.hudView));
        float countX = (width - TextPainter.width(count, countSize(), true)) / 2.0f;
        TextPainter.draw(context, count, countX, y, countSize(), Theme.TEXT, true);

        if (allowFlash && flashStart > 0) {
            float t = (Util.getMeasuringTimeMs() - flashStart) / (float) FLASH_MS;
            if (t < 1.0f) {
                int alpha = (int) (90 * (1.0f - t));
                Theme.roundedRect(context, 0, 0, width, height, radius, Theme.withAlpha(Theme.ACCENT, alpha));
            }
        }
    }

    /** Panel size in whole pixels: width, height. */
    private static int[] panelSize() {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        int padding = scaled(BASE_PADDING, 3);
        String count = Format.count(HelpData.get().countFor(config.hudView));

        float labelWidth = config.showLabel ? TextPainter.width(config.hudView.hudLabel, labelSize(), true) : 0;
        float countWidth = TextPainter.width(count, countSize(), true);
        int width = Math.max(scaled(BASE_MIN_WIDTH, 20),
                padding * 2 + Math.round(Math.max(labelWidth, countWidth)));

        int height = padding * 2 + Math.round(TextPainter.capHeight(countSize()));
        if (config.showLabel) {
            height += labelCapHeight() + scaled(3, 2) + 1 + scaled(5, 3);
        }
        return new int[]{width, height};
    }

    private static float labelSize() {
        return Math.max(4.0f, BASE_LABEL_SIZE * StaffTrackerConfig.get().hudScale);
    }

    private static float countSize() {
        return Math.max(10.0f, BASE_COUNT_SIZE * StaffTrackerConfig.get().hudScale);
    }

    private static int labelCapHeight() {
        return Math.round(TextPainter.capHeight(labelSize()));
    }

    private static int scaled(int base, int minimum) {
        return Math.max(minimum, Math.round(base * StaffTrackerConfig.get().hudScale));
    }

    /** Panel position in whole screen pixels: x, y. */
    private static int[] panelPosition(MinecraftClient client, int[] size) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int freeX = Math.max(0, screenWidth - size[0]);
        int freeY = Math.max(0, screenHeight - size[1]);
        return new int[]{
                Math.round(MathHelper.clamp(config.hudX, 0.0f, 1.0f) * freeX),
                Math.round(MathHelper.clamp(config.hudY, 0.0f, 1.0f) * freeY)
        };
    }
}
