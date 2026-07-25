package com.cosmicplayer.stafftracker.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Base for the mod's flat widgets. Vanilla's PressableWidget forces the
 * vanilla button look, so this adds simple press handling on top of
 * ClickableWidget instead. Enter and Space activate focused the widgets.
 */
public abstract class CleanWidget extends ClickableWidget {
    protected CleanWidget(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    /** Called on click or keyboard activation. Widgets that need the click position override onClick instead. */
    protected void onPress() {
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        onPress();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (this.active && this.visible && input.isEnterOrSpace()) {
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            onPress();
            return true;
        }
        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
