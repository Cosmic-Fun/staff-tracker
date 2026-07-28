package com.cosmicplayer.stafftracker.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * A small flat search box in the mod's own font. Click to focus, type
 * to filter, Escape clears.
 */
public class SearchField extends CleanWidget {
    private static final int MAX_LENGTH = 20;

    private final Consumer<String> onChange;
    private final String placeholder;
    private String query = "";

    public SearchField(int x, int y, int width, int height, String placeholder, Consumer<String> onChange) {
        super(x, y, width, height, Text.literal(placeholder));
        this.placeholder = placeholder;
        this.onChange = onChange;
    }

    /** Empties the box without firing the change callback. Used when the page changes. */
    public void reset() {
        query = "";
    }

    @Override
    protected void onPress() {
        setFocused(true);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!isFocused() || !input.isValidChar() || query.length() >= MAX_LENGTH) {
            return false;
        }
        setQuery(query + input.asString());
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!isFocused()) {
            return false;
        }
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            setQuery(query.substring(0, query.length() - 1));
            return true;
        }
        if (input.isEscape() && !query.isEmpty()) {
            setQuery("");
            return true;
        }
        return false;
    }

    private void setQuery(String value) {
        query = value;
        onChange.accept(value);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // A soft box that lifts slightly while focused, with an accent ring.
        int radius = 4;
        Theme.roundedRect(context, getX(), getY(), width, height, radius, isFocused() ? 0x1AFFFFFF : 0x10FFFFFF);
        if (isFocused()) {
            Theme.roundedRectOutline(context, getX(), getY(), width, height, radius,
                    Theme.withAlpha(Theme.ACCENT, 0x66), 0.5f);
        }

        float textX = getX() + 8;
        if (query.isEmpty()) {
            TextPainter.drawInRow(context, placeholder, textX, getY(), height,
                    Theme.FONT_SMALL, Theme.TEXT_DIM, false);
        } else {
            TextPainter.drawInRow(context, query, textX, getY(), height, Theme.FONT_BODY, Theme.TEXT, false);
        }

        // A blinking cursor bar while focused.
        if (isFocused() && Util.getMeasuringTimeMs() % 1000 < 500) {
            int cursorX = Math.round(textX + (query.isEmpty() ? 0 : TextPainter.width(query, Theme.FONT_BODY, false) + 1));
            context.fill(cursorX, getY() + 4, cursorX + 1, getY() + height - 4, Theme.TEXT_DIM);
        }
    }
}
