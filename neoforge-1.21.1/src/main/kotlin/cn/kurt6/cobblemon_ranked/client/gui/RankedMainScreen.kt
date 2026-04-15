package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class RankedMainScreen : Screen(Component.translatable("screen.cobblemon_ranked.title")) {
    companion object {
        private const val BUTTON_WIDTH = 160
        private const val BUTTON_HEIGHT = 20
        private const val BUTTON_SPACING = 25
    }

    private data class RankedButton(
        val labelKey: String,
        val command: String? = null,
        val tooltipKey: String? = null,
        val onClick: (() -> Unit)? = null
    )

    private val infoButtons = listOf(
        RankedButton("gui.cobblemon_ranked.button.gui", "rank gui", "tooltip.cobblemon_ranked.button.gui"),
        RankedButton("gui.cobblemon_ranked.button.stats", "rank gui_myinfo", "tooltip.cobblemon_ranked.button.stats"),
        RankedButton("gui.cobblemon_ranked.button.season", "rank season", "tooltip.cobblemon_ranked.button.season"),
        RankedButton("gui.cobblemon_ranked.button.leaderboard", "rank gui_top", "tooltip.cobblemon_ranked.button.leaderboard")
    )

    private val actionButtons = listOf(
        RankedButton("gui.cobblemon_ranked.button.join_singles", "rank queue join singles", "tooltip.cobblemon_ranked.button.join_singles"),
        RankedButton("gui.cobblemon_ranked.button.join_doubles", "rank queue join doubles", "tooltip.cobblemon_ranked.button.join_doubles"),
        RankedButton("gui.cobblemon_ranked.button.join_2v2singles", "rank queue join 2v2singles", "tooltip.cobblemon_ranked.button.join_2v2singles"),
        RankedButton("gui.cobblemon_ranked.button.leave", "rank queue leave", "tooltip.cobblemon_ranked.button.leave"),
        RankedButton("gui.cobblemon_ranked.button.close", tooltipKey = "tooltip.cobblemon_ranked.button.close", onClick = { onClose() })
    )

    override fun init() {
        val centerX = width / 2 - 10
        val centerY = height / 2

        var offsetY = centerY - (infoButtons.size * BUTTON_SPACING + 40)

        val infoTitle = StringWidget(Component.literal("§f§l▶ Cobblemon Rank"), font)
        infoTitle.setPosition(centerX - BUTTON_WIDTH / 2, offsetY)
        addRenderableWidget(infoTitle)
        offsetY += BUTTON_SPACING

        infoButtons.forEach { button ->
            addRankedButton(centerX, offsetY, button)
            offsetY += BUTTON_SPACING
        }

        offsetY += 10

        actionButtons.forEach { button ->
            addRankedButton(centerX, offsetY, button)
            offsetY += BUTTON_SPACING
        }
    }

    private fun addRankedButton(x: Int, y: Int, data: RankedButton) {
        val label = Component.translatable(data.labelKey).copy().withStyle(
            Style.EMPTY.withColor(0xFFFFFF)
        )

        val button = Button.builder(label) {
            data.onClick?.invoke() ?: data.command?.let { sendCommand(it) }
            minecraft?.setScreen(null)
        }.bounds(x - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()

        data.tooltipKey?.let {
            button.tooltip = Tooltip.create(Component.translatable(it))
        }

        addRenderableWidget(button)
    }

    private fun sendCommand(command: String) {
        minecraft?.player?.connection?.sendCommand(command)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xAA000000.toInt())
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
}
