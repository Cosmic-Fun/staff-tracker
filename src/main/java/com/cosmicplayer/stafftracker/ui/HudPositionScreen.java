package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.StaffTrackerConfig;
import com.cosmicplayer.stafftracker.hud.CounterHud;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Fullscreen editor for the HUD position. Drag the counter to move it,
 * scroll to resize it. The live panel is the actual HUD renderer, so
 * what you see is exactly what you get in game.
 */
public class HudPositionScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    public HudPositionScreen(Screen parent) {
        super(Text.literal("Adjust HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CounterHud.suppressed = true;
        int buttonWidth = 90;
        int y = this.height - 30;
        addDrawableChild(new CleanButton(this.width / 2 - buttonWidth - 3, y, buttonWidth, 20, "Reset Position", b -> {
            StaffTrackerConfig config = StaffTrackerConfig.get();
            config.hudX = StaffTrackerConfig.DEFAULT_HUD_X;
            config.hudY = StaffTrackerConfig.DEFAULT_HUD_Y;
            config.hudScale = StaffTrackerConfig.DEFAULT_HUD_SCALE;
        }));
        addDrawableChild(new CleanButton(this.width / 2 + 3, y, buttonWidth, 20, "Done", b -> close())
                .withStyle(CleanButton.Style.ACCENT));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // A soft glow tries to keep the hint readable over bright terrain without a backdrop.
        int size = Math.round(StaffTrackerConfig.get().hudScale * 100);
        drawGlowText(context, "Drag the counter to move it.", 14, Theme.FONT_BODY, Theme.TEXT, true);
        drawGlowText(context, "Scroll to change size  ·  " + size + "%", 26, Theme.FONT_SMALL, 0xFFD4D4DC, false);
        CounterHud.renderPanel(context, this.client, false);
    }

    /** Centered text over two rings of translucent copies, which blend into a glow. */
    private void drawGlowText(DrawContext context, String text, float y, float size, int color, boolean bold) {
        float centerX = this.width / 2.0f;
        float[] radii = {0.7f, 1.5f};
        int[] alphas = {0x38000000, 0x1E000000};
        for (int ring = 0; ring < radii.length; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI / 4 * i;
                float dx = (float) (Math.cos(angle) * radii[ring]);
                float dy = (float) (Math.sin(angle) * radii[ring]);
                TextPainter.drawCentered(context, text, centerX + dx, y + dy, size, alphas[ring], bold);
            }
        }
        TextPainter.drawCentered(context, text, centerX, y, size, color, bold);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // A light dim only. The game world stays visible while positioning.
        context.fill(0, 0, this.width, this.height, 0x50000000);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float[] bounds = CounterHud.panelBounds(this.client);
        if (click.x() >= bounds[0] && click.x() <= bounds[0] + bounds[2]
                && click.y() >= bounds[1] && click.y() <= bounds[1] + bounds[3]) {
            dragging = true;
            dragOffsetX = (float) click.x() - bounds[0];
            dragOffsetY = (float) click.y() - bounds[1];
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            moveTo((float) click.x() - dragOffsetX, (float) click.y() - dragOffsetY);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        config.hudScale = MathHelper.clamp(config.hudScale + (float) verticalAmount * 0.01f, 0.5f, 2.0f);
        return true;
    }

    /** Converts an absolute pixel position back into the stored screen fractions. */
    private void moveTo(float x, float y) {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        float[] bounds = CounterHud.panelBounds(this.client);
        float freeX = this.width - bounds[2];
        float freeY = this.height - bounds[3];
        config.hudX = freeX <= 0 ? 0.0f : MathHelper.clamp(x / freeX, 0.0f, 1.0f);
        config.hudY = freeY <= 0 ? 0.0f : MathHelper.clamp(y / freeY, 0.0f, 1.0f);
    }

    @Override
    public void removed() {
        CounterHud.suppressed = false;
        StaffTrackerConfig.save();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
