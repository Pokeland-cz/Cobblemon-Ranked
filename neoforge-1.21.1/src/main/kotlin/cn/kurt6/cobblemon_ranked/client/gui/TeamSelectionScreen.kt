package cn.kurt6.cobblemon_ranked.client.gui

import cn.kurt6.cobblemon_ranked.network.SelectionPokemonInfo
import cn.kurt6.cobblemon_ranked.network.TeamSelectionSubmitPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

class TeamSelectionScreen(
    private val limit: Int,
    private val timeLimit: Int,
    private val opponentName: String,
    private val opponentTeam: List<SelectionPokemonInfo>,
    private val myTeam: List<SelectionPokemonInfo>
) : Screen(Component.translatable("cobblemon_ranked.selection.title")) {
    private val selectedIndices = mutableListOf<Int>()
    private lateinit var confirmButton: Button

    private var timeRemaining = timeLimit
    private var lastTick = System.currentTimeMillis()

    private val myCardWidth = 72
    private val myCardHeight = 36
    private val gapX = 8
    private val gapY = 8

    override fun shouldCloseOnEsc(): Boolean {
        return false
    }

    override fun init() {
        super.init()
        val centerX = width / 2

        confirmButton = addRenderableWidget(
            Button.builder(Component.translatable("cobblemon_ranked.selection.confirm")) {
                submitSelection()
            }.bounds(centerX - 50, height - 26, 100, 20).build()
        )
        confirmButton.active = false
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val centerX = width / 2
            val totalWidth = (3 * myCardWidth) + (2 * gapX)
            val startX = centerX - (totalWidth / 2)
            val startY = height / 2 + 10

            myTeam.forEachIndexed { index, _ ->
                val col = index % 3
                val row = index / 3
                val x = startX + col * (myCardWidth + gapX)
                val y = startY + row * (myCardHeight + gapY)

                if (mouseX >= x && mouseX <= x + myCardWidth && mouseY >= y && mouseY <= y + myCardHeight) {
                    toggleSelection(index)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun toggleSelection(index: Int) {
        if (selectedIndices.contains(index)) {
            selectedIndices.remove(index)
        } else if (selectedIndices.size < limit) {
            selectedIndices.add(index)
        }
        updateButtonState()
    }

    private fun updateButtonState() {
        if (selectedIndices.size == limit) {
            confirmButton.active = true
            confirmButton.message = Component.translatable("cobblemon_ranked.selection.confirm")
        } else {
            confirmButton.active = false
            confirmButton.message = Component.translatable("cobblemon_ranked.selection.pick", selectedIndices.size, limit)
        }
    }

    private fun submitSelection() {
        val sortedUuids = selectedIndices.map { myTeam[it].uuid }
        PacketDistributor.sendToServer(TeamSelectionSubmitPayload(sortedUuids))
        onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fillGradient(0, 0, width, height, -1072689136, -804253680)

        drawHeader(guiGraphics)
        drawOpponentSection(guiGraphics)
        drawMyTeamSection(guiGraphics, mouseX, mouseY)

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun drawHeader(guiGraphics: GuiGraphics) {
        val centerX = width / 2

        if (System.currentTimeMillis() - lastTick > 1000) {
            timeRemaining--
            lastTick = System.currentTimeMillis()
            if (timeRemaining <= 0) {
                onClose()
            }
        }

        val timeColor = if (timeRemaining < 10) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt()
        val timeText = Component.translatable("cobblemon_ranked.selection.time", timeRemaining)
        guiGraphics.drawCenteredString(font, timeText, width - 60, 15, timeColor)

        val titleText = Component.translatable("cobblemon_ranked.selection.title")
        guiGraphics.drawCenteredString(font, titleText, centerX, 15, 0xFFD700)
    }

    private fun drawOpponentSection(guiGraphics: GuiGraphics) {
        val centerX = width / 2
        val startY = 40

        val opponentText = Component.translatable("cobblemon_ranked.selection.opponent", opponentName)
        guiGraphics.drawCenteredString(font, opponentText, centerX, startY - 10, 0xFFFFAA.toInt())

        val opCardWidth = 70
        val opCardHeight = 35
        val opGap = 5
        val totalWidth = (3 * opCardWidth) + (2 * opGap)
        val startX = centerX - (totalWidth / 2)

        opponentTeam.forEachIndexed { index, info ->
            val col = index % 3
            val row = index / 3
            val x = startX + col * (opCardWidth + opGap)
            val y = startY + row * (opCardHeight + opGap)

            guiGraphics.fill(x, y, x + opCardWidth, y + opCardHeight, 0x66550000)
            guiGraphics.renderOutline(x, y, opCardWidth, opCardHeight, 0xFFAA0000.toInt())
            renderOpponentPokemon(guiGraphics, info, x, y, opCardWidth)
        }
    }

    private fun drawMyTeamSection(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val centerX = width / 2
        val startY = height / 2 + 10

        val teamText = Component.translatable("cobblemon_ranked.selection.your_team")
        guiGraphics.drawCenteredString(font, teamText, centerX, startY - 12, 0xAAFFAA.toInt())

        val totalWidth = (3 * myCardWidth) + (2 * gapX)
        val startX = centerX - (totalWidth / 2)

        myTeam.forEachIndexed { index, info ->
            val col = index % 3
            val row = index / 3
            val x = startX + col * (myCardWidth + gapX)
            val y = startY + row * (myCardHeight + gapY)

            val isSelected = selectedIndices.contains(index)
            val isHovered = mouseX >= x && mouseX <= x + myCardWidth && mouseY >= y && mouseY <= y + myCardHeight

            var bgColor = 0x66000000
            var borderColor = 0xFF555555.toInt()

            if (isSelected) {
                bgColor = 0x88004400.toInt()
                borderColor = 0xFF00FF00.toInt()
            } else if (isHovered) {
                bgColor = 0x66333333
                borderColor = 0xFFAAAAAA.toInt()
            }

            guiGraphics.fill(x, y, x + myCardWidth, y + myCardHeight, bgColor)
            guiGraphics.renderOutline(x, y, myCardWidth, myCardHeight, borderColor)

            if (isSelected) {
                val order = selectedIndices.indexOf(index) + 1
                guiGraphics.drawString(font, "#$order", x + myCardWidth - 15, y + 2, 0xFF00FF00.toInt(), true)
            }

            renderMyPokemon(guiGraphics, info, x, y, myCardWidth, index)
        }
    }

    private fun renderOpponentPokemon(guiGraphics: GuiGraphics, info: SelectionPokemonInfo, x: Int, y: Int, width: Int) {
        val localizedName = getLocalizedPokemonName(info.species)
        val nameColor = if (info.shiny) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
        val displayName = if (localizedName.length > 9) "${localizedName.substring(0, 8)}…" else localizedName
        guiGraphics.drawString(font, displayName, x + 4, y + 4, nameColor, true)

        val genderStr = when (info.gender) {
            "MALE" -> "♂"
            "FEMALE" -> "♀"
            else -> ""
        }
        val levelText = Component.translatable("cobblemon_ranked.selection.lvl", info.level).string
        guiGraphics.drawString(font, "$levelText $genderStr", x + 4, y + 15, 0xFFAAAAAA.toInt(), true)

        if (info.shiny) {
            guiGraphics.drawString(font, "✨", x + width - 12, y + 15, 0xFFFFD700.toInt(), true)
        }
    }

    private fun renderMyPokemon(guiGraphics: GuiGraphics, info: SelectionPokemonInfo, x: Int, y: Int, width: Int, index: Int) {
        val localizedName = getLocalizedPokemonName(info.species)
        val displayName = if (info.displayName != info.species) {
            if (info.displayName.length > 12) "${info.displayName.substring(0, 11)}…" else info.displayName
        } else {
            if (localizedName.length > 9) "${localizedName.substring(0, 8)}…" else localizedName
        }

        val nameColor = if (info.shiny) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
        guiGraphics.drawString(font, displayName, x + 4, y + 4, nameColor, true)

        val genderStr = when (info.gender) {
            "MALE" -> "♂"
            "FEMALE" -> "♀"
            else -> ""
        }
        val levelText = Component.translatable("cobblemon_ranked.selection.lvl", info.level).string
        guiGraphics.drawString(font, "$levelText $genderStr", x + 4, y + 15, 0xFFAAAAAA.toInt(), true)

        if (info.shiny) {
            guiGraphics.drawString(font, "✨", x + width - 12, y + 15, 0xFFFFD700.toInt(), true)
        }

        if (!selectedIndices.contains(index) && selectedIndices.size >= limit) {
            guiGraphics.fill(x, y, x + width, y + 36, 0xAA000000.toInt())
        }
    }

    private fun getLocalizedPokemonName(englishName: String): String {
        val key = "cobblemon.species.${englishName.lowercase()}.name"
        val translated = Component.translatable(key).string
        return if (translated == key) englishName else translated
    }

    override fun isPauseScreen(): Boolean = false
}
