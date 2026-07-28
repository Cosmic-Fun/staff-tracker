package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.CountKeyListener;
import com.cosmicplayer.stafftracker.StaffTrackerConfig;
import com.cosmicplayer.stafftracker.StaffTrackerConfig.CountKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * A settings row showing the counter bind as a key cap, like CTRL + H.
 * Click it, then press the new combo. Keys and mouse buttons both work,
 * with any mix of Ctrl, Shift, and Alt. Escape cancels.
 */
public class KeybindRow extends CleanWidget {
    private static final int CHIP_HEIGHT = 13;

    private final String label;
    private boolean listening;
    private int heldSideButtons;

    public KeybindRow(int x, int y, int width, int height, String label) {
        super(x, y, width, height, Text.literal(label));
        this.label = label;
    }

    public boolean isListening() {
        return listening;
    }

    @Override
    protected void onPress() {
        listening = true;
        // Remember which side buttons are already down so they do not bind instantly.
        heldSideButtons = sideButtonState();
    }

    /** Applies a key press while listening. Modifier keys alone keep the capture open. */
    public void handleKey(KeyInput input) {
        if (input.isEscape()) {
            listening = false;
            return;
        }
        if (isModifierKey(input.key())) {
            return;
        }
        apply(CountKey.KEYBOARD, input.key(),
                (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0,
                (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0,
                (input.modifiers() & GLFW.GLFW_MOD_ALT) != 0);
    }

    /** Applies a mouse button press while listening. */
    public void handleMouse(Click click) {
        apply(CountKey.MOUSE, click.button(),
                (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0,
                (click.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0,
                (click.modifiers() & GLFW.GLFW_MOD_ALT) != 0);
    }

    /**
     * Side mouse buttons do not reliably reach screens as clicks because the
     * game treats them as GUI navigation, so this polls them directly while
     * the row is listening. Even with this, detection can still be spotty.
     */
    private void pollSideButtons() {
        long window = window();
        for (int button = GLFW.GLFW_MOUSE_BUTTON_4; button <= GLFW.GLFW_MOUSE_BUTTON_LAST; button++) {
            boolean down = GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
            int bit = 1 << button;
            boolean wasDown = (heldSideButtons & bit) != 0;
            heldSideButtons = down ? heldSideButtons | bit : heldSideButtons & ~bit;
            if (down && !wasDown) {
                apply(CountKey.MOUSE, button,
                        CountKeyListener.eitherKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT),
                        CountKeyListener.eitherKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL),
                        CountKeyListener.eitherKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT));
                return;
            }
        }
    }

    private int sideButtonState() {
        long window = window();
        int state = 0;
        for (int button = GLFW.GLFW_MOUSE_BUTTON_4; button <= GLFW.GLFW_MOUSE_BUTTON_LAST; button++) {
            if (GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS) {
                state |= 1 << button;
            }
        }
        return state;
    }

    private void apply(String device, int code, boolean shift, boolean ctrl, boolean alt) {
        CountKey key = StaffTrackerConfig.get().countKey;
        key.device = device;
        key.code = code;
        key.shift = shift;
        key.ctrl = ctrl;
        key.alt = alt;
        StaffTrackerConfig.save();
        listening = false;
    }

    private static boolean isModifierKey(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private static long window() {
        return MinecraftClient.getInstance().getWindow().getHandle();
    }

    /** The chip label, like CTRL + SHIFT + H or BUTTON 5. */
    private static String bindLabel() {
        CountKey key = StaffTrackerConfig.get().countKey;
        InputUtil.Type type = key.isMouse() ? InputUtil.Type.MOUSE : InputUtil.Type.KEYSYM;
        String name = type.createFromCode(key.code).getLocalizedText().getString().toUpperCase(Locale.ROOT);

        StringBuilder label = new StringBuilder();
        if (key.ctrl) {
            label.append("CTRL + ");
        }
        if (key.shift) {
            label.append("SHIFT + ");
        }
        if (key.alt) {
            label.append("ALT + ");
        }
        return label.append(name).toString();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (listening) {
            pollSideButtons();
        }
        TextPainter.drawInRow(context, label, getX() + 2, getY(), height, Theme.FONT_BODY, Theme.TEXT, false);

        String chipText = listening ? "Select a Keybind" : bindLabel();
        int chipTextColor = listening ? Theme.ACCENT : Theme.TEXT;

        // The chip sizes itself to the text so the label always sits centered.
        int textWidth = Math.round(TextPainter.width(chipText, Theme.FONT_SMALL, false));
        int chipWidth = Math.max(textWidth + 12, CHIP_HEIGHT + 4);
        int chipX = getX() + width - 2 - chipWidth;
        int chipY = getY() + (height - CHIP_HEIGHT) / 2;
        Theme.roundedRect(context, chipX, chipY, chipWidth, CHIP_HEIGHT, 3, Theme.CHIP);
        TextPainter.drawCenteredInRow(context, chipText, chipX + chipWidth / 2.0f, chipY, CHIP_HEIGHT,
                Theme.FONT_SMALL, chipTextColor, false);
    }
}
