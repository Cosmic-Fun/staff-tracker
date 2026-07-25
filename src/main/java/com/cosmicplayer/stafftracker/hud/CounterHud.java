package com.cosmicplayer.stafftracker.hud;

import com.cosmicplayer.stafftracker.HelpData;
import com.cosmicplayer.stafftracker.StaffTrackerClient;
import com.cosmicplayer.stafftracker.StaffTrackerConfig;
import com.cosmicplayer.stafftracker.StaffTrackerConfig.HudView;
import com.cosmicplayer.stafftracker.ui.Format;
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
 * The HUD size setting never scale transforms text. Fractional scaling
 * drops pixel rows on standard displays because font textures are sampled
 * without filtering, so the setting picks real font sizes from the bundled
 * ladder and everything draws at native resolution on whole pixels.
 */
public final class CounterHud {
    private static final int BACKGROUND = 0x8C0E0E14;
    private static final int BORDER = 0x3CFFFFFF;
    private static final int DIVIDER = 0x2EFFFFFF;
    private static final int FLASH_MS = 400;

    // Base metrics at 100% size. Everything scales from these, rounded to
    // whole pixels. Caps in Inter are about 73% of the font size, and the
    // game puts every text baseline 7 pixels below the draw position.
    private static final int BASE_LABEL_SIZE = 8;
    private static final int BASE_COUNT_SIZE = 20;
    private static final int BASE_PADDING = 7;
    private static final int BASE_RADIUS = 6;
    private static final int BASE_MIN_WIDTH = 46;
    private static final float CAP_RATIO = 0.73f;
    private static final int FONT_BASELINE = 7;

    private static long flashStart = -1;

    // Styled Text objects are immutable, so the label and count are cached
    // instead of being rebuilt every frame.
    private static Text labelText;
    private static HudView labelView;
    private static int labelSizeUsed = -1;
    private static Text countText;
    private static int countValue = -1;
    private static HudView countView;
    private static int countSizeUsed = -1;

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
            Text message = Theme.text("Helped +1  ·  " + Format.count(HelpData.get().today()) + " today");
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
        int[] size = panelSize(client);
        int[] position = panelPosition(client, size);

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(position[0], position[1]);
        drawPanel(context, client, size[0], size[1], allowFlash);
        matrices.popMatrix();
    }

    /** Panel bounds in screen pixels: x, y, width, height. Used for dragging. */
    public static float[] panelBounds(MinecraftClient client) {
        int[] size = panelSize(client);
        int[] position = panelPosition(client, size);
        return new float[]{position[0], position[1], size[0], size[1]};
    }

    private static void drawPanel(DrawContext context, MinecraftClient client, int width, int height, boolean allowFlash) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        int padding = scaled(BASE_PADDING, 3);
        int radius = scaled(BASE_RADIUS, 3);
        Theme.roundedRect(context, 0, 0, width, height, radius, BACKGROUND);
        Theme.roundedRectOutline(context, 0, 0, width, height, radius, BORDER);

        int y = padding;
        if (config.showLabel) {
            int labelSize = labelSize();
            context.drawText(client.textRenderer, labelText(config.hudView),
                    padding, y + capHeight(labelSize) - FONT_BASELINE, Theme.TEXT_DIM, false);
            y += capHeight(labelSize) + scaled(3, 2);
            context.fill(padding, y, width - padding, y + 1, DIVIDER);
            y += 1 + scaled(5, 3);
        }

        // With the label the count sits flush left under it. Without, it centers.
        int countSize = countSize();
        Text count = countText(config.hudView);
        int countX = config.showLabel
                ? padding
                : (width - client.textRenderer.getWidth(count)) / 2;
        context.drawText(client.textRenderer, count,
                countX, y + capHeight(countSize) - FONT_BASELINE, Theme.TEXT, false);

        if (allowFlash && flashStart > 0) {
            float t = (Util.getMeasuringTimeMs() - flashStart) / (float) FLASH_MS;
            if (t < 1.0f) {
                int alpha = (int) (90 * (1.0f - t));
                Theme.roundedRect(context, 0, 0, width, height, radius, Theme.withAlpha(Theme.ACCENT, alpha));
            }
        }
    }

    /** Panel size in whole pixels: width, height. */
    private static int[] panelSize(MinecraftClient client) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        int padding = scaled(BASE_PADDING, 3);
        Text count = countText(config.hudView);

        int labelWidth = config.showLabel ? client.textRenderer.getWidth(labelText(config.hudView)) : 0;
        int countWidth = client.textRenderer.getWidth(count);
        int width = Math.max(scaled(BASE_MIN_WIDTH, 20), padding * 2 + Math.max(labelWidth, countWidth));

        int height = padding * 2 + capHeight(countSize());
        if (config.showLabel) {
            height += capHeight(labelSize()) + scaled(3, 2) + 1 + scaled(5, 3);
        }
        return new int[]{width, height};
    }

    private static int labelSize() {
        return MathHelper.clamp(scaled(BASE_LABEL_SIZE, 4), 4, 16);
    }

    private static int countSize() {
        return MathHelper.clamp(scaled(BASE_COUNT_SIZE, 10), 10, 40);
    }

    private static int capHeight(int fontSize) {
        return Math.round(fontSize * CAP_RATIO);
    }

    private static int scaled(int base, int minimum) {
        return Math.max(minimum, Math.round(base * StaffTrackerConfig.get().hudScale));
    }

    private static Text labelText(HudView view) {
        int size = labelSize();
        if (labelText == null || view != labelView || size != labelSizeUsed) {
            labelView = view;
            labelSizeUsed = size;
            labelText = Theme.textBoldSized(view.hudLabel, size);
        }
        return labelText;
    }

    private static Text countText(HudView view) {
        int value = HelpData.get().countFor(view);
        int size = countSize();
        if (countText == null || value != countValue || view != countView || size != countSizeUsed) {
            countValue = value;
            countView = view;
            countSizeUsed = size;
            countText = Theme.textBoldSized(Format.count(value), size);
        }
        return countText;
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
