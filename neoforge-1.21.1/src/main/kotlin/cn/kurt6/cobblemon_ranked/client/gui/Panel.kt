package cn.kurt6.cobblemon_ranked.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class Panel(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val texture: ResourceLocation
) : AbstractWidget(x, y, width, height, Component.empty()) {
    var panelAlpha: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (panelAlpha <= 0f) return

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1f, 1f, 1f, panelAlpha)
        guiGraphics.blit(texture, x, y, width, height, 0f, 0f, width, height, width, height)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}
}
