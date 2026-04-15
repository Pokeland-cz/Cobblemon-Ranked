package cn.kurt6.cobblemon_ranked.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class ModeSwitchButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val mode: String,
    private val currentMode: String,
    private val textureNormal: ResourceLocation,
    private val textureActive: ResourceLocation,
    private val onClickAction: () -> Unit,
    private val baseAlpha: Float = 0.4f
) : AbstractWidget(x, y, width, height, Component.empty()) {
    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hovered = isHovered
        val textureToUse = if (mode == currentMode) textureActive else textureNormal
        val renderAlpha = if (hovered) 0.6f else baseAlpha

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1f, 1f, 1f, renderAlpha)
        guiGraphics.blit(textureToUse, x, y, width, height, 0f, 0f, width, height, width, height)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onClick(mouseX: Double, mouseY: Double) {
        onClickAction()
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}
}
