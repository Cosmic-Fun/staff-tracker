package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.StaffTrackerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's own text renderer. The game's font system rounds every letter
 * to whole GUI pixels, which makes small text uneven and clips edges. This
 * one draws Inter onto texture sheets, called atlases, at the display's
 * real pixel resolution, then shows them one to one with screen pixels.
 * Text stays even and sharp at any GUI scale and any size.
 *
 * Sizes are in GUI units like the rest of the layout code. A draw call's
 * y is the top of the capital letters, which makes row centering simple.
 */
public final class TextPainter {
    /** The characters that get baked, ASCII and Latin-1. Anything else draws as "?". */
    private static final char[] CHARS = buildCharSet();
    private static final int ATLAS_WIDTH = 512;
    private static final int CELL_PADDING = 2;

    /** One character: its spot on the atlas, its draw offset, and how far text moves after it. All in real pixels. */
    private record Glyph(int u, int v, int width, int height, int xOffset, int yOffset, float advance) {}

    /** One baked font size: its atlas texture, its characters, and the measurements layout needs. */
    private record Atlas(Identifier texture, int textureWidth, int textureHeight,
                         Map<Character, Glyph> glyphs, float capHeight, float lineHeight) {}

    private record AtlasKey(boolean bold, int sizeCenti) {}

    private static final Map<AtlasKey, Atlas> atlases = new HashMap<>();
    private static float lastDensity = -1;
    private static Font semiBoldFont;
    private static Font boldFont;
    private static int nextTextureId = 0;

    private TextPainter() {
    }

    /** Draws text with its capital letters starting at the given y. */
    public static void draw(DrawContext context, String text, float x, float y, float size, int color, boolean bold) {
        float density = density();
        Atlas atlas = atlas(size, bold, density);

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(1.0f / density, 1.0f / density);

        float penX = x * density;
        int baseline = Math.round(y * density + atlas.capHeight());
        for (int i = 0; i < text.length(); i++) {
            Glyph glyph = glyphFor(atlas, text.charAt(i));
            if (glyph.width() > 0) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, atlas.texture(),
                        Math.round(penX) + glyph.xOffset(), baseline + glyph.yOffset(),
                        glyph.u(), glyph.v(), glyph.width(), glyph.height(),
                        glyph.width(), glyph.height(), atlas.textureWidth(), atlas.textureHeight(), color);
            }
            penX += glyph.advance();
        }
        matrices.popMatrix();
    }

    public static void drawCentered(DrawContext context, String text, float centerX, float y, float size, int color, boolean bold) {
        draw(context, text, centerX - width(text, size, bold) / 2.0f, y, size, color, bold);
    }

    /** Draws text vertically centered in a row. */
    public static void drawInRow(DrawContext context, String text, float x, float rowY, float rowHeight,
                                 float size, int color, boolean bold) {
        draw(context, text, x, rowY + (rowHeight - capHeight(size)) / 2.0f, size, color, bold);
    }

    /** Draws text centered both ways in a row. */
    public static void drawCenteredInRow(DrawContext context, String text, float centerX, float rowY, float rowHeight,
                                         float size, int color, boolean bold) {
        drawInRow(context, text, centerX - width(text, size, bold) / 2.0f, rowY, rowHeight, size, color, bold);
    }

    /** The text width in GUI units. */
    public static float width(String text, float size, boolean bold) {
        Atlas atlas = atlas(size, bold, density());
        float total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += glyphFor(atlas, text.charAt(i)).advance();
        }
        return total / density();
    }

    /** Capital letter height in GUI units. Rows center text with this. */
    public static float capHeight(float size) {
        return atlas(size, false, density()).capHeight() / density();
    }

    /** Full line height in GUI units, ascent plus descent. */
    public static float lineHeight(float size) {
        return atlas(size, false, density()).lineHeight() / density();
    }

    /** Simple word wrap. Words wider than the limit get split mid word. */
    public static List<String> wrap(String text, float size, boolean bold, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (width(word, size, bold) > maxWidth && word.length() > 1) {
                int cut = word.length() - 1;
                while (cut > 1 && width(word.substring(0, cut), size, bold) > maxWidth) {
                    cut--;
                }
                flush(lines, line);
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate, size, bold) > maxWidth) {
                flush(lines, line);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        flush(lines, line);
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static void flush(List<String> lines, StringBuilder line) {
        if (!line.isEmpty()) {
            lines.add(line.toString());
            line.setLength(0);
        }
    }

    private static Glyph glyphFor(Atlas atlas, char c) {
        Glyph glyph = atlas.glyphs().get(c);
        return glyph != null ? glyph : atlas.glyphs().get('?');
    }

    /** Real screen pixels per GUI unit. Above one on high density displays. Theme shapes use this too. */
    static float density() {
        MinecraftClient client = MinecraftClient.getInstance();
        return (float) client.getWindow().getFramebufferWidth() / client.getWindow().getScaledWidth();
    }

    private static Atlas atlas(float size, boolean bold, float density) {
        // A GUI scale change makes every cached atlas the wrong resolution.
        if (density != lastDensity) {
            clearAtlases();
            lastDensity = density;
        }
        AtlasKey key = new AtlasKey(bold, Math.round(size * 100));
        Atlas atlas = atlases.get(key);
        if (atlas == null) {
            atlas = bake(size * density, bold);
            atlases.put(key, atlas);
        }
        return atlas;
    }

    /** Frees the old textures along with the cache, so they do not pile up in memory. */
    private static void clearAtlases() {
        for (Atlas atlas : atlases.values()) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(atlas.texture());
        }
        atlases.clear();
    }

    /** Draws one font size onto an atlas texture with Java's font engine. */
    private static Atlas bake(float pixelSize, boolean bold) {
        Font font = (bold ? boldFont() : semiBoldFont()).deriveFont(pixelSize);
        FontRenderContext metricsContext = new FontRenderContext(null, true, true);

        // First pass: measure every character and lay out the atlas grid.
        Map<Character, Rectangle> bounds = new HashMap<>();
        Map<Character, Float> advances = new HashMap<>();
        int rowHeight = 0;
        for (char c : CHARS) {
            GlyphVector vector = font.createGlyphVector(metricsContext, String.valueOf(c));
            Rectangle box = vector.getPixelBounds(metricsContext, 0, 0);
            bounds.put(c, box);
            advances.put(c, vector.getGlyphMetrics(0).getAdvanceX());
            rowHeight = Math.max(rowHeight, box.height);
        }
        rowHeight += CELL_PADDING;

        Map<Character, Glyph> glyphs = new HashMap<>(CHARS.length);
        int x = CELL_PADDING;
        int y = CELL_PADDING;
        for (char c : CHARS) {
            Rectangle box = bounds.get(c);
            if (x + box.width + CELL_PADDING > ATLAS_WIDTH) {
                x = CELL_PADDING;
                y += rowHeight;
            }
            glyphs.put(c, new Glyph(x, y, box.width, box.height, box.x, box.y, advances.get(c)));
            x += box.width + CELL_PADDING;
        }
        int textureHeight = y + rowHeight;

        // Second pass: draw the characters in white so draw calls can tint them any color.
        BufferedImage image = new BufferedImage(ATLAS_WIDTH, textureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        for (char c : CHARS) {
            Glyph glyph = glyphs.get(c);
            graphics.drawString(String.valueOf(c), glyph.u() - glyph.xOffset(), glyph.v() - glyph.yOffset());
        }
        graphics.dispose();

        NativeImage nativeImage = new NativeImage(ATLAS_WIDTH, textureHeight, true);
        for (int py = 0; py < textureHeight; py++) {
            for (int px = 0; px < ATLAS_WIDTH; px++) {
                nativeImage.setColorArgb(px, py, image.getRGB(px, py));
            }
        }
        Identifier id = StaffTrackerClient.id("font_atlas/" + nextTextureId++);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                new NativeImageBackedTexture(id::toString, nativeImage));

        float capHeight = bounds.get('H').height;
        float lineHeight = font.getLineMetrics("Hg", metricsContext).getHeight();
        return new Atlas(id, ATLAS_WIDTH, textureHeight, glyphs, capHeight, lineHeight);
    }

    private static Font semiBoldFont() {
        if (semiBoldFont == null) {
            semiBoldFont = loadFont("inter-semibold.ttf");
        }
        return semiBoldFont;
    }

    private static Font boldFont() {
        if (boldFont == null) {
            boldFont = loadFont("inter-bold.ttf");
        }
        return boldFont;
    }

    private static Font loadFont(String file) {
        try (InputStream stream = TextPainter.class.getResourceAsStream("/assets/stafftracker/font/" + file)) {
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (Exception e) {
            // A missing font file would be a broken build. Fall back to any sans.
            return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
    }

    private static char[] buildCharSet() {
        StringBuilder chars = new StringBuilder();
        for (char c = 32; c < 127; c++) {
            chars.append(c);
        }
        for (char c = 160; c < 256; c++) {
            chars.append(c);
        }
        // Extras the UI uses that sit outside Latin-1.
        chars.append('‹').append('›').append('…');
        return chars.toString().toCharArray();
    }
}
