package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.StaffTrackerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared colors, font sizes, and shape drawing for the mod's UI.
 * Text itself renders through TextPainter.
 *
 * Rounded corners come from small circle textures, drawn once with Java's
 * anti aliasing and cached. Their soft edges keep every shape smooth on
 * any display, where plain fills would show hard pixel steps.
 */
public final class Theme {
    public static final int PANEL = 0xF2090909;
    public static final int TEXT = 0xFFF2F2F5;
    public static final int TEXT_DIM = 0xFF8C8C8C;
    public static final int DANGER = 0xFFCF6060;
    public static final int ACCENT = 0xFFB158E6;
    public static final int HOVER = 0x12FFFFFF;
    public static final int CHIP = 0x1FFFFFFF;
    public static final int TRACK = 0xFF2E2E2E;
    public static final int TRACK_DARK = 0xFF141414;
    public static final int RAIL = 0x08FFFFFF;
    public static final int THUMB = 0xFF3A3A3A;

    /** The two text sizes the UI uses, in GUI units. */
    public static final float FONT_BODY = 8.0f;
    public static final float FONT_SMALL = 7.0f;

    /** Baked circle textures by radius in real pixels. Rings key on radius and thickness. */
    private static final Map<Integer, Identifier> circles = new HashMap<>();
    private static final Map<Integer, Identifier> rings = new HashMap<>();
    private static int nextShapeId = 0;

    private Theme() {
    }

    /** Draws a flat rectangle with smooth rounded corners. */
    public static void roundedRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        roundedRectEdges(context, x, y, width, height, radius, color, true, true);
    }

    /** Rounded rectangle where each vertical edge can be square instead. Used by the dock rail. */
    public static void roundedRectEdges(DrawContext context, int x, int y, int width, int height, int radius,
                                        int color, boolean roundLeft, boolean roundRight) {
        float density = TextPainter.density();
        int w = Math.round(width * density);
        int h = Math.round(height * density);
        int r = pixelRadius(radius, width, height, density, w, h);
        Identifier circle = circle(r);

        // Three fills cover the straight parts without overlapping, which
        // matters because most of these colors are translucent.
        Matrix3x2fStack matrices = beginPixelSpace(context, x, y, density);
        context.fill(0, r, w, h - r, color);
        int left = roundLeft ? r : 0;
        int right = roundRight ? r : 0;
        context.fill(left, 0, w - right, r, color);
        context.fill(left, h - r, w - right, h, color);
        if (roundLeft) {
            corner(context, circle, 0, 0, 0, 0, r, color);
            corner(context, circle, 0, h - r, 0, r, r, color);
        }
        if (roundRight) {
            corner(context, circle, w - r, 0, r, 0, r, color);
            corner(context, circle, w - r, h - r, r, r, r, color);
        }
        matrices.popMatrix();
    }

    /** A rounded border with an even one pixel stroke. */
    public static void roundedRectOutline(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        roundedRectOutline(context, x, y, width, height, radius, color, 1.0f);
    }

    /**
     * A rounded border with an even stroke. Thickness is in GUI pixels, so
     * 0.5 reads as a hairline. The straight sides are plain fills and the
     * corners come from a baked ring texture, so the whole border stays
     * smooth at one even thickness.
     */
    public static void roundedRectOutline(DrawContext context, int x, int y, int width, int height, int radius,
                                          int color, float thickness) {
        float density = TextPainter.density();
        int w = Math.round(width * density);
        int h = Math.round(height * density);
        int r = pixelRadius(radius, width, height, density, w, h);
        int t = MathHelper.clamp(Math.round(thickness * density), 1, r);
        Identifier ring = ring(r, t);

        Matrix3x2fStack matrices = beginPixelSpace(context, x, y, density);
        context.fill(r, 0, w - r, t, color);
        context.fill(r, h - t, w - r, h, color);
        context.fill(0, r, t, h - r, color);
        context.fill(w - t, r, w, h - r, color);
        corner(context, ring, 0, 0, 0, 0, r, color);
        corner(context, ring, w - r, 0, r, 0, r, color);
        corner(context, ring, 0, h - r, 0, r, r, color);
        corner(context, ring, w - r, h - r, r, r, r, color);
        matrices.popMatrix();
    }

    /** The corner radius in real pixels, capped so opposite corners never overlap. */
    private static int pixelRadius(int radius, int width, int height, float density, int w, int h) {
        int r = Math.round(Math.min(radius, Math.min(width, height) / 2) * density);
        return MathHelper.clamp(r, 1, Math.min(w, h) / 2);
    }

    /** Moves to the shape's corner and scales down so drawing lands on real screen pixels. */
    private static Matrix3x2fStack beginPixelSpace(DrawContext context, int x, int y, float density) {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(1.0f / density, 1.0f / density);
        return matrices;
    }

    /** Draws one quadrant of a circle texture, tinted. The u and v pick the quadrant. */
    private static void corner(DrawContext context, Identifier texture, int x, int y, int u, int v, int r, int color) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, r, r, r, r, r * 2, r * 2, color);
    }

    /** A filled white circle texture for the given radius, baked on first use. */
    private static Identifier circle(int radius) {
        return circles.computeIfAbsent(radius, r -> {
            BufferedImage image = new BufferedImage(r * 2, r * 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = smoothGraphics(image);
            graphics.fill(new Ellipse2D.Float(0, 0, r * 2, r * 2));
            graphics.dispose();
            return register(image);
        });
    }

    /** A white circle stroke texture for the given radius and thickness, baked on first use. */
    private static Identifier ring(int radius, int thickness) {
        return rings.computeIfAbsent(radius * 1000 + thickness, key -> {
            BufferedImage image = new BufferedImage(radius * 2, radius * 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = smoothGraphics(image);
            graphics.fill(new Ellipse2D.Float(0, 0, radius * 2, radius * 2));
            // Punch out the middle so just the stroke is left.
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fill(new Ellipse2D.Float(thickness, thickness,
                    radius * 2 - thickness * 2, radius * 2 - thickness * 2));
            graphics.dispose();
            return register(image);
        });
    }

    private static Graphics2D smoothGraphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        return graphics;
    }

    /** Uploads the image as a game texture and returns its id. */
    private static Identifier register(BufferedImage image) {
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setColorArgb(x, y, image.getRGB(x, y));
            }
        }
        Identifier id = StaffTrackerClient.id("shape/" + nextShapeId++);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                new NativeImageBackedTexture(id::toString, nativeImage));
        return id;
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
