package cn.kurt6.cobblemon_ranked.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents

abstract class StandardImageButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val texture: ResourceLocation,
    private val hoverOverlay: ResourceLocation? = ResourceLocation.fromNamespaceAndPath(
        "cobblemon_ranked",
        "textures/gui/hover_overlay.png"
    )
) : AbstractWidget(x, y, width, height, Component.empty()) {
    private var hoverProgress = 0f
    private val animationSpeed = 0.2f

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val target = if (isHovered) 1f else 0f
        hoverProgress += (target - hoverProgress) * animationSpeed

        guiGraphics.blit(texture, x, y, width, height, 0f, 0f, width, height, width, height)

        if (hoverProgress > 0.01f && hoverOverlay != null) {
            val inset = 1
            val overlayWidth = width - inset * 2
            val overlayHeight = height - inset * 2

            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShaderColor(1f, 1f, 1f, hoverProgress)

            guiGraphics.blit(
                hoverOverlay,
                x + inset,
                y + inset,
                overlayWidth,
                overlayHeight,
                0f,
                0f,
                overlayWidth,
                overlayHeight,
                overlayWidth,
                overlayHeight
            )

            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            RenderSystem.disableBlend()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onClick(mouseX: Double, mouseY: Double) {
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)
        )
        onClicked()
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}

    abstract fun onClicked()
}
