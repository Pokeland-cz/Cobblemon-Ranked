package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class RankedMainMenuScreen : RankedBaseScreen(Component.literal("Cobblemon")) {
    override fun init() {
        super.init()

        val minecraft = Minecraft.getInstance()
        val scaleX = minecraft.window.guiScaledWidth / 1920f
        val scaleY = minecraft.window.guiScaledHeight / 1080f

        val buttonWidth = (400 * 0.9f * scaleX).toInt()
        val buttonHeight = (107 * 0.9f * scaleY).toInt()
        val spacingY = (80 * scaleY).toInt()
        val spacingX = (80 * scaleX).toInt()

        val totalHeight = 3 * buttonHeight + 2 * spacingY
        val startY = uiY + uiHeight / 2 - totalHeight / 2
        val totalWidth = 2 * buttonWidth + spacingX
        val startX = uiX + uiWidth / 2 - totalWidth / 2

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

        val langSuffix = langSuffix()
        val buttons = listOf(
            Triple("singles", "btn_singles_$langSuffix.png", 0),
            Triple("doubles", "btn_doubles_$langSuffix.png", 0),
            Triple("2v2singles", "btn_2v2singles_$langSuffix.png", 1),
            Triple("exit_queue", "button_cancel_$langSuffix.png", 1),
            Triple("text_menu", "btn_text_$langSuffix.png", 2),
            Triple("cross_server", "btn_cross_$langSuffix.png", 2)
        )

        buttons.chunked(2).forEachIndexed { row, pair ->
            pair.forEachIndexed { col, (mode, textureFile, _) ->
                val x = startX + col * (buttonWidth + spacingX)
                val y = startY + row * (buttonHeight + spacingY)
                val texture = ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/$textureFile")

                addRenderableWidget(object : StandardImageButton(x, y, buttonWidth, buttonHeight, texture) {
                    override fun onClicked() {
                        val player = minecraft.player ?: return

                        when (mode) {
                            "text_menu" -> {
                                player.connection.sendCommand("rank gui")
                                onClose()
                            }

                            "exit_queue" -> {
                                player.connection.sendCommand("rank queue leave")
                                onClose()
                            }

                            "cross_server" -> minecraft.setScreen(CrossServerScreen())
                            else -> minecraft.setScreen(ModeScreen(mode))
                        }
                    }
                })
            }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val minecraft = Minecraft.getInstance()
        minecraft.gui.chat.render(guiGraphics, minecraft.gui.guiTicks, mouseX, mouseY, false)

        val title = Component.translatable("screen.cobblemon_ranked.title")
        val titleX = uiX + uiWidth / 2 - font.width(title) / 2
        val titleY = uiY + (30 * (minecraft.window.guiScaledHeight / 1080f)).toInt()
        guiGraphics.drawString(font, title, titleX, titleY, 0xFFFFFF, true)

        val scaleFactor = 0.5f
        val madeByText = Component.literal("By Kurt")
        val textWidth = (font.width(madeByText) * scaleFactor).toInt()
        val textHeight = (font.lineHeight * scaleFactor).toInt()
        val madeByX = width - textWidth - 3
        val madeByY = height - textHeight - 3

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(madeByX.toFloat(), madeByY.toFloat(), 0f)
        guiGraphics.pose().scale(scaleFactor, scaleFactor, 1f)
        guiGraphics.drawString(font, madeByText, 0, 0, 0xAAAAAA, false)
        guiGraphics.pose().popPose()
    }
}
