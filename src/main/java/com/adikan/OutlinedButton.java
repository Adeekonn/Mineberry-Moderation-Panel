package com.adikan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class OutlinedButton extends Button {

    private final int borderColor;
    private final int hoverBorderColor;
    private final int textColor;
    private final BooleanSupplier isActive;

    public OutlinedButton(int x, int y, int width, int height, Component message,
                          int borderColor, int hoverBorderColor, int textColor,
                          OnPress onPress) {
        this(x, y, width, height, message, borderColor, hoverBorderColor, textColor, onPress, () -> false);
    }

    public OutlinedButton(int x, int y, int width, int height, Component message,
                          int borderColor, int hoverBorderColor, int textColor,
                          OnPress onPress, BooleanSupplier isActive) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.borderColor = borderColor;
        this.hoverBorderColor = hoverBorderColor;
        this.textColor = textColor;
        this.isActive = isActive;
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        boolean active = isActive.getAsBoolean();

        // Active = white border + white text
        // Hovered = bright color border + white text
        // Normal = dim color border + dim text
        int border = active ? 0xFFFFFFFF : (isHovered() ? hoverBorderColor : borderColor);
        int text   = active ? 0xFFFFFFFF : (isHovered() ? 0xFFFFFF : textColor);

        graphics.renderOutline(getX(), getY(), width, height, border);
        graphics.drawCenteredString(
                Minecraft.getInstance().font,
                getMessage(),
                getX() + width / 2,
                getY() + (height - 8) / 2,
                text
        );
    }
}