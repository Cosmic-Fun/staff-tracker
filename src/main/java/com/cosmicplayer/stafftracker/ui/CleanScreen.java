package com.cosmicplayer.stafftracker.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Base for the mod's screens. Draws a centered rounded panel with a title
 * and eases the whole panel in with a short slide when the screen opens.
 */
public abstract class CleanScreen extends Screen {
    private static final int OPEN_SLIDE_MS = 120;
    private static final int OPEN_SLIDE_DISTANCE = 12;

    protected final Screen parent;
    protected final String titleLabel;
    protected int panelWidth = 260;
    protected int panelHeight = 200;

    private final long openedAt = Util.getMeasuringTimeMs();
    private boolean slideActive;

    protected CleanScreen(String title, Screen parent) {
        super(Text.literal(title));
        this.titleLabel = title;
        this.parent = parent;
    }

    protected int panelX() {
        return (this.width - panelWidth) / 2;
    }

    protected int panelY() {
        return (this.height - panelHeight) / 2;
    }

    protected int contentX() {
        return panelX() + 12;
    }

    protected int contentWidth() {
        return panelWidth - 24;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        renderBehindPanel(context, mouseX, mouseY, delta);

        // Slide everything down from a slight offset while the screen opens.
        // The matrix is pushed here and popped in render, after the widgets
        // and the subclass content have drawn, so the whole panel moves as one.
        float progress = Math.min(1.0f, (Util.getMeasuringTimeMs() - openedAt) / (float) OPEN_SLIDE_MS);
        float eased = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, (1.0f - eased) * OPEN_SLIDE_DISTANCE);
        slideActive = true;

        Theme.roundedRect(context, panelX(), panelY(), panelWidth, panelHeight, 6, Theme.PANEL);
        if (showTitle()) {
            TextPainter.drawCentered(context, titleLabel, this.width / 2.0f, panelY() + 10,
                    Theme.FONT_BODY, Theme.TEXT, true);
        }
    }

    /** Screens that draw their own header, like the dock window, turn this off. */
    protected boolean showTitle() {
        return true;
    }

    @Override
    public final void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        renderContent(context, mouseX, mouseY, delta);
        if (slideActive) {
            context.getMatrices().popMatrix();
            slideActive = false;
        }
    }

    /** Extra drawing on top of the panel and widgets. Runs inside the slide transform. */
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    /**
     * Drawing between the dimmed world and the panel, like the live HUD
     * preview. Stays put during the open animation and never gets blurred.
     */
    protected void renderBehindPanel(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
