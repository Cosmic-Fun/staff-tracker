package com.cosmicplayer.stafftracker.mixin;

import com.cosmicplayer.stafftracker.ui.CleanButton;
import com.cosmicplayer.stafftracker.ui.StaffTrackerScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a Staff Tracker button to the top right of the pause menu. Other
 * mods add their own buttons there at unpredictable times, so the button
 * re-anchors every frame: below the corner's existing button at the same
 * size when there is one, alone in the corner otherwise. This For you Rtzy.
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final int MARGIN = 8;
    private static final int BUTTON_GAP = 4;
    private static final int DEFAULT_WIDTH = 90;
    private static final int DEFAULT_HEIGHT = 20;

    @Unique
    private CleanButton stafftracker$button;

    @Shadow
    public abstract boolean shouldShowMenu();

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void stafftracker$addButton(CallbackInfo ci) {
        stafftracker$button = null;
        if (!this.shouldShowMenu()) {
            return;
        }
        stafftracker$button = new CleanButton(this.width - DEFAULT_WIDTH - MARGIN, MARGIN,
                DEFAULT_WIDTH, DEFAULT_HEIGHT, "Staff Tracker",
                b -> this.client.setScreen(new StaffTrackerScreen(this))).glass();
        this.addDrawableChild(stafftracker$button);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void stafftracker$anchorButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (stafftracker$button == null) {
            return;
        }
        ClickableWidget neighbor = stafftracker$findTopRightButton();
        if (neighbor != null) {
            stafftracker$button.setDimensionsAndPosition(neighbor.getWidth(), neighbor.getHeight(),
                    neighbor.getX(), neighbor.getBottom() + BUTTON_GAP);
        } else {
            stafftracker$button.setDimensionsAndPosition(DEFAULT_WIDTH, DEFAULT_HEIGHT,
                    this.width - DEFAULT_WIDTH - MARGIN, MARGIN);
        }
    }

    /** The lowest button already sitting in the top right corner, excluding the mod's own. */
    @Unique
    private ClickableWidget stafftracker$findTopRightButton() {
        ClickableWidget lowest = null;
        for (Element element : this.children()) {
            if (!(element instanceof ClickableWidget widget) || widget == stafftracker$button) {
                continue;
            }
            boolean topRight = widget.getX() + widget.getWidth() >= this.width - 20
                    && widget.getY() < this.height / 3;
            if (topRight && (lowest == null || widget.getY() > lowest.getY())) {
                lowest = widget;
            }
        }
        return lowest;
    }
}
