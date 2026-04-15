package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class CrossServerScreen : RankedBaseScreen(Component.translatable("cobblemon_ranked.cross_server.title")) {
    override fun init() {
        super.init()

        val minecraft = Minecraft.getInstance()
        val scaleX = minecraft.window.guiScaledWidth / 1920f
        val scaleY = minecraft.window.guiScaledHeight / 1080f
        val langSuffix = langSuffix()

        addRenderableWidget(object : StandardImageButton(
            (uiX + uiWidth - 90 * scaleX).toInt(),
            (uiY + 20 * scaleY).toInt(),
            (80 * 0.85f * scaleX).toInt(),
            (73 * 0.85f * scaleY).toInt(),
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/btn_close.png"),
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/hover_overlay_btn_close.png")
        ) {
            override fun onClicked() {
                onClose()
            }
        })

        val backButtonWidth = (400 * 0.85f * scaleX).toInt()
        val backButtonHeight = (107 * 0.85f * scaleY).toInt()
        val backButtonX = uiX + uiWidth / 2 - backButtonWidth / 2
        val backButtonY = uiY + uiHeight - (150 * scaleY).toInt()

        addRenderableWidget(object : StandardImageButton(
            backButtonX,
            backButtonY,
            backButtonWidth,
            backButtonHeight,
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_back_$langSuffix.png")
        ) {
            override fun onClicked() {
                minecraft.setScreen(RankedMainMenuScreen())
            }
        })

        val buttonWidth = (400 * 0.85f * scaleX).toInt()
        val buttonHeight = (107 * 0.85f * scaleY).toInt()
        val spacingY = (50 * scaleY).toInt()
        val centerX = uiX + uiWidth / 2 - buttonWidth / 2
        val centerY = uiY + uiHeight / 2 - (2 * buttonHeight + spacingY) / 2

        addRenderableWidget(object : StandardImageButton(
            centerX,
            centerY,
            buttonWidth,
            buttonHeight,
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/btn_singles_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank cross join singles")
            }
        })

        addRenderableWidget(object : StandardImageButton(
            centerX,
            centerY + buttonHeight + spacingY,
            buttonWidth,
            buttonHeight,
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_cancel_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank cross leave")
            }
        })
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val minecraft = Minecraft.getInstance()
        val scaleX = minecraft.window.guiScaledWidth / 1920f
        val scaleY = minecraft.window.guiScaledHeight / 1080f

        minecraft.gui.chat.render(guiGraphics, minecraft.gui.guiTicks, mouseX, mouseY, false)

        val title = Component.translatable("screen.cobblemon_ranked.title")
        val titleX = uiX + uiWidth / 2 - font.width(title) / 2
        val titleY = uiY + (30 * (minecraft.window.guiScaledHeight / 1080f)).toInt()
        guiGraphics.drawString(font, title, titleX, titleY, 0xFFFFFF, true)

        val scaleFactor = 0.5f
        val madeByText = Component.literal("By Kurt")
        val textWidth = (font.width(madeByText) * scaleFactor).toInt()
        val textHeight = (font.lineHeight * scaleFactor).toInt()
        val madeByX = width - textWidth - (10 * scaleX).toInt() - 2
        val madeByY = height - textHeight - (10 * scaleY).toInt() - 2

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(madeByX.toFloat(), madeByY.toFloat(), 0f)
        guiGraphics.pose().scale(scaleFactor, scaleFactor, 1f)
        guiGraphics.drawString(font, madeByText, 0, 0, 0xAAAAAA, true)
        guiGraphics.pose().popPose()
    }

    private fun sendCommand(command: String) {
        minecraft?.player?.connection?.sendCommand(command.removePrefix("/"))
    }
}
