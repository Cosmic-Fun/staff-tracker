package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.StaffTrackerClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

/** Shared colors, fonts, and drawing helpers for the mod's UI. */
public final class Theme {
    public static final int PANEL = 0xF20E0E13;
    public static final int TEXT = 0xFFF2F2F5;
    public static final int TEXT_DIM = 0xFF8A8A96;
    public static final int DANGER = 0xFFCF6060;
    public static final int ACCENT = 0xFFB158E6;
    public static final int HOVER = 0x12FFFFFF;
    public static final int CHIP = 0x1FFFFFFF;
    public static final int TRACK = 0xFF33333E;
    public static final int TRACK_DARK = 0xFF19191F;
    public static final int RAIL = 0x08FFFFFF;
    public static final int THUMB = 0xFF3A3A45;

    /**
     * Corner curves are drawn at this subdivision so they rasterize with
     * sub pixel steps. It also makes it look smooth and not pixelated.
     */
    private static final int SMOOTH = 4;

    private static final StyleSpriteSource.Font FONT = new StyleSpriteSource.Font(StaffTrackerClient.id("clean"));
    private static final StyleSpriteSource.Font FONT_SMALL = new StyleSpriteSource.Font(StaffTrackerClient.id("clean_small"));
    private static final StyleSpriteSource.Font FONT_BOLD = new StyleSpriteSource.Font(StaffTrackerClient.id("clean_bold"));

    private Theme() {
    }

    /** Text in the mod's clean font. */
    public static MutableText text(String value) {
        return Text.literal(value).styled(style -> style.withFont(FONT));
    }

    /** Caption sized text for labels, headers, and secondary info. */
    public static MutableText textSmall(String value) {
        return Text.literal(value).styled(style -> style.withFont(FONT_SMALL));
    }

    /** SemiBold text. Used where thin strokes would be hard to read, like the HUD. */
    public static MutableText textBold(String value) {
        return Text.literal(value).styled(style -> style.withFont(FONT_BOLD));
    }

    /** Draws a flat rectangle with smooth rounded corners. */
    public static void roundedRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        roundedRectEdges(context, x, y, width, height, radius, color, true, true);
    }

    /** Rounded rectangle where each vertical edge can be square instead. Used by the dock rail. */
    public static void roundedRectEdges(DrawContext context, int x, int y, int width, int height, int radius,
                                        int color, boolean roundLeft, boolean roundRight) {
        int r = Math.min(radius, Math.min(width, height) / 2) * SMOOTH;
        Matrix3x2fStack matrices = beginSmooth(context, x, y);
        int w = width * SMOOTH;
        int h = height * SMOOTH;

        context.fill(0, r, w, h - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            int left = roundLeft ? inset : 0;
            int right = roundRight ? inset : 0;
            context.fill(left, i, w - right, i + 1, color);
            context.fill(left, h - i - 1, w - right, h - i, color);
        }
        matrices.popMatrix();
    }

    /** A rounded border with an even one pixel stroke. */
    public static void roundedRectOutline(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        roundedRectOutline(context, x, y, width, height, radius, color, 1.0f);
    }

    /**
     * A rounded border with an even stroke. Thickness is in GUI pixels, so
     * 0.5 reads as a hairline. Each row fills the gap between the outer
     * shape and the same shape inset by the stroke, which keeps the corners
     * the same thickness as the straight sides. Keep this in mind for future changes.
     */
    public static void roundedRectOutline(DrawContext context, int x, int y, int width, int height, int radius,
                                          int color, float thickness) {
        int r = Math.min(radius, Math.min(width, height) / 2) * SMOOTH;
        int t = Math.max(1, Math.round(SMOOTH * thickness));
        int w = width * SMOOTH;
        int h = height * SMOOTH;
        int innerRadius = Math.max(0, r - t);

        Matrix3x2fStack matrices = beginSmooth(context, x, y);
        for (int i = 0; i < h; i++) {
            int outer = ringInset(i, h, r);
            if (i < t || i >= h - t) {
                context.fill(outer, i, w - outer, i + 1, color);
                continue;
            }
            int inner = Math.max(outer + t, t + ringInset(i - t, h - 2 * t, innerRadius));
            inner = Math.min(inner, w / 2);
            context.fill(outer, i, inner, i + 1, color);
            context.fill(w - inner, i, w - outer, i + 1, color);
        }
        matrices.popMatrix();
    }

    /** Horizontal inset of a rounded shape at the given row. Zero along the straight sides. */
    private static int ringInset(int row, int height, int radius) {
        if (row < radius) {
            return cornerInset(radius, row);
        }
        if (row >= height - radius) {
            return cornerInset(radius, height - 1 - row);
        }
        return 0;
    }

    /** Scales the matrix down so fills drawn in SMOOTH units land on sub pixel bounds. */
    private static Matrix3x2fStack beginSmooth(DrawContext context, int x, int y) {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(1.0f / SMOOTH, 1.0f / SMOOTH);
        return matrices;
    }

    private static int cornerInset(int r, int row) {
        double distance = r - row - 0.5;
        return r - (int) Math.round(Math.sqrt(r * r - distance * distance));
    }

    public static void drawCenteredText(DrawContext context, TextRenderer textRenderer, Text text, int centerX, int y, int color) {
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, false);
    }

    /**
     * Centered text over a soft dark glow. Two rings of low alpha passes
     * blend into a feathered shadow, so the text stays readable over any
     * world without a hard outline or a background box.
     * COMEBACK: Come up with a better idea, this looks like shit.
     */
    public static void drawCenteredHaloText(DrawContext context, TextRenderer textRenderer, Text text, int centerX, int y, int color) {
        int x = centerX - textRenderer.getWidth(text) / 2;
        Matrix3x2fStack matrices = context.getMatrices();
        float[] radii = {0.7f, 1.5f};
        int[] alphas = {0x38000000, 0x1E000000};
        for (int ring = 0; ring < radii.length; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI / 4 * i;
                matrices.pushMatrix();
                matrices.translate((float) (Math.cos(angle) * radii[ring]), (float) (Math.sin(angle) * radii[ring]));
                context.drawText(textRenderer, text, x, y, alphas[ring], false);
                matrices.popMatrix();
            }
        }
        context.drawText(textRenderer, text, x, y, color, false);
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    /** Blends two ARGB colors. Used for small hover and toggle animations. */
    public static int lerpColor(int from, int to, float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        int a = (int) MathHelper.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = (int) MathHelper.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) MathHelper.lerp(t, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
