package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.StaffTrackerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small player head icons for the chat and history pages. Heads download
 * from mc-heads.net by name, once each, and stay cached for the session.
 * Until a head arrives, or if it never does, a colored tile with the
 * player's first letter stands in.
 */
public final class PlayerHeads {
    private static final int SIZE = 16;
    private static final int[] TILE_COLORS = {
            0xFF5B8DD9, 0xFF56A87A, 0xFFB86FCF, 0xFFCF8B56, 0xFFC95E7C, 0xFF5FA8B8,
    };

    /** Downloaded head textures by lowercase name. A null value means the fetch failed. */
    private static final Map<String, Identifier> heads = new HashMap<>();
    private static final Set<String> pending = new HashSet<>();

    private PlayerHeads() {
    }

    /** Draws a player's head at the given size. Falls back to a letter tile. */
    public static void draw(DrawContext context, String name, int x, int y, int size) {
        Identifier head = headFor(name);
        if (head != null) {
            // The full avatar region scales down into the quad.
            context.drawTexture(RenderPipelines.GUI_TEXTURED, head, x, y, 0, 0, size, size, SIZE, SIZE, SIZE, SIZE);
            return;
        }
        int color = TILE_COLORS[Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), TILE_COLORS.length)];
        Theme.roundedRect(context, x, y, size, size, 3, color);
        TextPainter.drawCenteredInRow(context, name.substring(0, 1).toUpperCase(Locale.ROOT),
                x + size / 2.0f, y, size, Theme.FONT_SMALL, Theme.TEXT, true);
    }

    /** A neutral placeholder head for counts with no player identified. */
    public static void drawUnknown(DrawContext context, int x, int y, int size) {
        Theme.roundedRect(context, x, y, size, size, 3, Theme.THUMB);
        TextPainter.drawCenteredInRow(context, "?", x + size / 2.0f, y, size,
                Theme.FONT_SMALL, Theme.TEXT_DIM, true);
    }

    /** The cached head texture, kicking off a download the first time a name is seen. */
    private static Identifier headFor(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (heads.containsKey(key)) {
            return heads.get(key);
        }
        if (pending.add(key)) {
            fetch(key);
        }
        return null;
    }

    private static void fetch(String key) {
        Util.getDownloadWorkerExecutor().execute(() -> {
            NativeImage image = download(key);
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                Identifier id = null;
                if (image != null) {
                    id = StaffTrackerClient.id("head/" + key);
                    client.getTextureManager().registerTexture(id,
                            new NativeImageBackedTexture(() -> "stafftracker head " + key, image));
                }
                heads.put(key, id);
                pending.remove(key);
            });
        });
    }

    private static NativeImage download(String key) {
        try (InputStream stream = URI.create("https://mc-heads.net/avatar/" + key + "/" + SIZE).toURL().openStream()) {
            return NativeImage.read(stream);
        } catch (Exception e) {
            return null;
        }
    }
}
