package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

object NotificationManager {
    private var notificationText: Component? = null
    private var displayTicks = 0
    private const val DISPLAY_TIME = 100

    fun show(text: Component) {
        notificationText = text
        displayTicks = DISPLAY_TIME
    }

    fun render(guiGraphics: GuiGraphics, screenWidth: Int, screenHeight: Int, font: Font) {
        val text = notificationText ?: return
        if (displayTicks <= 0) return

        val x = screenWidth / 2 - font.width(text) / 2
        val y = (screenHeight * 0.75f).toInt()
        guiGraphics.drawString(font, text, x, y, 0xFFFFFF, true)
        displayTicks--
    }
}
