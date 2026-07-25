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
 */
public final class CounterHud {
    private static final int BACKGROUND = 0x8C0E0E14;
    private static final int BORDER = 0x3CFFFFFF;
    private static final int DIVIDER = 0x2EFFFFFF;
    private static final int CORNER_RADIUS = 6;
    private static final int FLASH_MS = 400;
    private static final int PADDING = 7;
    private static final int LABEL_HEIGHT = 9;
    private static final float COUNT_SCALE = 2.5f;
    private static final int MIN_WIDTH = 46;

    // Digits span from row DIGIT_TOP down to the baseline of the 9 pixel tall
    // text line, with no descenders. Sizing to that band keeps the vertical
    // padding around the count even instead of leaving a gap underneath.
    private static final int FONT_BASELINE = 7;
    private static final int DIGIT_TOP = 1;

    private static long flashStart = -1;

    // Styled Text objects are immutable, so the label and count are cached
    // instead of being rebuilt every frame.
    private static final Text[] LABELS = new Text[HudView.values().length];
    private static Text countText;
    private static int countValue = -1;
    private static HudView countView;

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
        StaffTrackerConfig config = StaffTrackerConfig.get();
        int[] size = panelSize(client);
        float[] position = panelPosition(client, size);

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(position[0], position[1]);
        matrices.scale(config.hudScale, config.hudScale);
        drawPanel(context, client, size[0], size[1], allowFlash);
        matrices.popMatrix();
    }

    /** Panel bounds in screen pixels: x, y, width, height. Used for dragging. */
    public static float[] panelBounds(MinecraftClient client) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        int[] size = panelSize(client);
        float[] position = panelPosition(client, size);
        return new float[]{position[0], position[1], size[0] * config.hudScale, size[1] * config.hudScale};
    }

    private static void drawPanel(DrawContext context, MinecraftClient client, int width, int height, boolean allowFlash) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        Theme.roundedRect(context, 0, 0, width, height, CORNER_RADIUS, BACKGROUND);
        Theme.roundedRectOutline(context, 0, 0, width, height, CORNER_RADIUS, BORDER);

        int y = PADDING;
        if (config.showLabel) {
            context.drawText(client.textRenderer, labelText(config.hudView), PADDING, y, Theme.TEXT_DIM, false);
            y += LABEL_HEIGHT + 3;
            context.fill(PADDING, y, width - PADDING, y + 1, DIVIDER);
            y += 5;
        }

        // With the label the count sits flush left under it. Without, it centers.
        Text count = countText(config.hudView);
        float countX = config.showLabel
                ? PADDING
                : (width - client.textRenderer.getWidth(count) * COUNT_SCALE) / 2.0f;
        float countY = y - DIGIT_TOP * COUNT_SCALE;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(countX, countY);
        matrices.scale(COUNT_SCALE, COUNT_SCALE);
        context.drawText(client.textRenderer, count, 0, 0, Theme.TEXT, false);
        matrices.popMatrix();

        if (allowFlash && flashStart > 0) {
            float t = (Util.getMeasuringTimeMs() - flashStart) / (float) FLASH_MS;
            if (t < 1.0f) {
                int alpha = (int) (90 * (1.0f - t));
                Theme.roundedRect(context, 0, 0, width, height, CORNER_RADIUS, Theme.withAlpha(Theme.ACCENT, alpha));
            }
        }
    }

    /** Unscaled panel size: width, height. */
    private static int[] panelSize(MinecraftClient client) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        Text count = countText(config.hudView);

        int labelWidth = config.showLabel
                ? client.textRenderer.getWidth(labelText(config.hudView))
                : 0;
        int countWidth = Math.round(client.textRenderer.getWidth(count) * COUNT_SCALE);
        int width = Math.max(MIN_WIDTH, PADDING * 2 + Math.max(labelWidth, countWidth));

        int countHeight = Math.round((FONT_BASELINE - DIGIT_TOP) * COUNT_SCALE);
        int height = PADDING * 2 + countHeight;
        if (config.showLabel) {
            height += LABEL_HEIGHT + 3 + 1 + 5;
        }
        return new int[]{width, height};
    }

    private static Text labelText(HudView view) {
        Text label = LABELS[view.ordinal()];
        if (label == null) {
            label = Theme.textBold(view.hudLabel);
            LABELS[view.ordinal()] = label;
        }
        return label;
    }

    private static Text countText(HudView view) {
        int value = HelpData.get().countFor(view);
        if (countText == null || value != countValue || view != countView) {
            countValue = value;
            countView = view;
            countText = Theme.textBold(Format.count(value));
        }
        return countText;
    }

    /** Scaled panel position in screen pixels: x, y. */
    private static float[] panelPosition(MinecraftClient client, int[] size) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        float freeX = Math.max(0, screenWidth - size[0] * config.hudScale);
        float freeY = Math.max(0, screenHeight - size[1] * config.hudScale);
        return new float[]{
                MathHelper.clamp(config.hudX, 0.0f, 1.0f) * freeX,
                MathHelper.clamp(config.hudY, 0.0f, 1.0f) * freeY
        };
    }
}
