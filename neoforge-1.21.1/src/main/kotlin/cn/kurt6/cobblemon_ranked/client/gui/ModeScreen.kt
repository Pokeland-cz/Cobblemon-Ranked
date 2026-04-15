package cn.kurt6.cobblemon_ranked.client.gui

import cn.kurt6.cobblemon_ranked.network.RequestPlayerRankPayload
import cn.kurt6.cobblemon_ranked.network.RequestType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor

class ModeScreen(private val mode: String) : RankedBaseScreen(Component.literal("Cobblemon - $mode")) {
    companion object {
        fun modeName(mode: String): Component {
            return Component.translatable("cobblemon_ranked.mode.$mode")
        }
    }

    private var leaderboardPage = 1
    private var playerInfoLines: List<Component> = listOf(Component.translatable("screen.cobblemon_ranked.loading.player_info"))
    private var seasonInfoLines: List<Component> = listOf(Component.translatable("screen.cobblemon_ranked.loading.season_info"))
    private var leaderboardLines: List<Component> = listOf(Component.translatable("screen.cobblemon_ranked.loading.leaderboard"))

    private lateinit var playerInfoRenderer: FancyMultilineTextRenderer
    private lateinit var seasonInfoRenderer: FancyMultilineTextRenderer
    private lateinit var leaderboardRenderer: FancyMultilineTextRenderer

    private var leaderboardRegionX = 0
    private var leaderboardRegionY = 0
    private var leaderboardRegionWidth = 0
    private var leaderboardRegionHeight = 0
    private var playerInfoRegionX = 0
    private var playerInfoRegionY = 0
    private var playerInfoRegionWidth = 0
    private var playerInfoRegionHeight = 0
    private var seasonInfoRegionX = 0
    private var seasonInfoRegionY = 0
    private var seasonInfoRegionWidth = 0
    private var seasonInfoRegionHeight = 0

    private var seasonScrollOffset = 0
    private var playerScrollOffset = 0
    private var leaderboardScrollOffset = 0

    private val leaderboardWidth = 500
    private val leaderboardHeight = 800
    private val panelWidth = 500
    private val panelHeight = 350
    private val seasonPanelWidth = 500
    private val seasonPanelHeight = 400

    private val modeButtons = mutableListOf<ModeSwitchButton>()

    override fun init() {
        super.init()

        addModeSwitchButtons()

        val minecraft = Minecraft.getInstance()
        val scaleX = minecraft.window.guiScaledWidth / 1920f
        val scaleY = minecraft.window.guiScaledHeight / 1080f
        val langSuffix = langSuffix()

        val buttonWidth = (400 * 0.85f * scaleX).toInt()
        val buttonHeight = (107 * 0.85f * scaleY).toInt()
        val spacingY = (80 * scaleY).toInt()

        val totalHeight = 3 * buttonHeight + 2 * spacingY
        val startY = uiY + uiHeight / 2 - totalHeight / 2
        val centerX = uiX + uiWidth / 2 - buttonWidth / 2

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

        addRenderableWidget(object : StandardImageButton(
            centerX,
            startY,
            buttonWidth,
            buttonHeight,
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_match_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank queue join $mode")
                NotificationManager.show(Component.literal(getLocalizedText("§6已加入 $mode 匹配队列", "§cJoined the $mode matching queue")))
            }
        })

        addRenderableWidget(object : StandardImageButton(
            centerX,
            startY + buttonHeight + spacingY,
            buttonWidth,
            buttonHeight,
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_cancel_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank queue leave")
            }
        })

        addRenderableWidget(object : StandardImageButton(
            centerX,
            startY + 2 * (buttonHeight + spacingY),
            buttonWidth,
            buttonHeight,
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_state_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank status")
            }
        })

        addRenderableWidget(object : StandardImageButton(
            centerX,
            startY + 3 * (buttonHeight + spacingY),
            buttonWidth,
            buttonHeight,
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_back_$langSuffix.png")
        ) {
            override fun onClicked() {
                minecraft.setScreen(RankedMainMenuScreen())
            }
        })

        addRenderableWidget(
            ImageButton(
                (uiX + 240 * scaleX).toInt(),
                (uiY + 845 * scaleY).toInt(),
                (90 * scaleX).toInt(),
                (70 * scaleY).toInt(),
                ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_prev.png"),
                ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_prev_hover.png")
            ) {
                if (leaderboardPage > 1) {
                    leaderboardPage--
                    requestInfo(RequestType.LEADERBOARD)
                }
            }
        )

        addRenderableWidget(
            ImageButton(
                (uiX + 420 * scaleX).toInt(),
                (uiY + 845 * scaleY).toInt(),
                (90 * scaleX).toInt(),
                (70 * scaleY).toInt(),
                ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_next.png"),
                ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/button_next_hover.png")
            ) {
                leaderboardPage++
                requestInfo(RequestType.LEADERBOARD)
            }
        )

        requestInfo(RequestType.PLAYER)
        requestInfo(RequestType.SEASON)
        requestInfo(RequestType.LEADERBOARD)

        addRenderableWidget(
            Panel(
                (uiX + 120 * scaleX).toInt(),
                (uiY + 150 * scaleY).toInt(),
                (leaderboardWidth * scaleX).toInt(),
                (leaderboardHeight * scaleY).toInt(),
                ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/panel_leaderboard.png")
            ).apply {
                panelAlpha = 0.8f
            }
        )

        addRenderableWidget(
            Panel(
                (uiX + 1250 * scaleX).toInt(),
                (uiY + 150 * scaleY).toInt(),
                (panelWidth * scaleX).toInt(),
                (panelHeight * scaleY).toInt(),
                ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/panel_player.png")
            ).apply {
                panelAlpha = 0.8f
            }
        )

        addRenderableWidget(
            Panel(
                (uiX + 1250 * scaleX).toInt(),
                (uiY + 550 * scaleY).toInt(),
                (seasonPanelWidth * scaleX).toInt(),
                (seasonPanelHeight * scaleY).toInt(),
                ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/panel_season.png")
            ).apply {
                panelAlpha = 0.8f
            }
        )
    }

    private fun addModeSwitchButtons() {
        modeButtons.clear()

        val modes = listOf("singles", "doubles", "2v2singles")
        val scaleX = Minecraft.getInstance().window.guiScaledWidth / 1920f
        val scaleY = Minecraft.getInstance().window.guiScaledHeight / 1080f
        val buttonWidth = (130 * scaleX).toInt()
        val buttonHeight = (85 * scaleY).toInt()
        val spacingX = (50 * scaleX).toInt()
        val langSuffix = langSuffix()

        val totalWidth = modes.size * buttonWidth + (modes.size - 1) * spacingX
        val startX = uiX + uiWidth / 2 - totalWidth / 2
        val y = uiY + (30 * scaleY).toInt()

        modes.forEachIndexed { index, targetMode ->
            val base = "button_mode_${targetMode}"
            val textureNormal = ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/${base}_${langSuffix}.png")
            val textureActive = ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/${base}_active_${langSuffix}.png")

            val button = ModeSwitchButton(
                x = startX + index * (buttonWidth + spacingX),
                y = y,
                width = buttonWidth,
                height = buttonHeight,
                mode = targetMode,
                currentMode = mode,
                textureNormal = textureNormal,
                textureActive = textureActive,
                onClickAction = { minecraft?.setScreen(ModeScreen(targetMode)) },
                baseAlpha = if (targetMode == mode) 0.8f else 0.4f
            )
            addRenderableWidget(button)
            modeButtons.add(button)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)

        val minecraft = Minecraft.getInstance()
        val scaleX = minecraft.window.guiScaledWidth / 1920f
        val scaleY = minecraft.window.guiScaledHeight / 1080f

        minecraft.gui.chat.render(guiGraphics, minecraft.gui.guiTicks, mouseX, mouseY, false)

        val scaledLeaderboardWidth = (leaderboardWidth * scaleX).toInt()
        val scaledLeaderboardHeight = (leaderboardHeight * scaleY).toInt()
        val scaledPanelWidth = (panelWidth * scaleX).toInt()
        val scaledPanelHeight = (panelHeight * scaleY).toInt()
        val scaledSeasonPanelHeight = (seasonPanelHeight * scaleY).toInt()
        val panelMarginX = (100 * scaleX).toInt()
        val panelMarginY = (120 * scaleY).toInt()
        val textScale = ((scaleX + scaleY) / 2f).coerceAtLeast(0.2f)

        leaderboardRegionX = uiX + panelMarginX + 23
        leaderboardRegionY = uiY + panelMarginY + 26
        leaderboardRegionWidth = scaledLeaderboardWidth - 8
        leaderboardRegionHeight = scaledLeaderboardHeight - (90 * scaleX).toInt() - 5

        playerInfoRegionX = uiX + uiWidth - panelMarginX - scaledPanelWidth - 6
        playerInfoRegionY = uiY + panelMarginY + 27
        playerInfoRegionWidth = scaledPanelWidth - 8
        playerInfoRegionHeight = scaledPanelHeight - 8

        seasonInfoRegionX = playerInfoRegionX
        seasonInfoRegionY = uiY + panelMarginY + scaledPanelHeight + (20 * scaleY).toInt() + 40
        seasonInfoRegionWidth = scaledPanelWidth - 8
        seasonInfoRegionHeight = scaledSeasonPanelHeight - 8

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        playerInfoRenderer = FancyMultilineTextRenderer(
            lines = playerInfoLines,
            x = playerInfoRegionX,
            y = playerInfoRegionY,
            width = playerInfoRegionWidth,
            height = playerInfoRegionHeight,
            scale = textScale,
            lineSpacing = 5,
            alignCenter = false,
            drawShadow = true,
            scrollOffset = playerScrollOffset
        )
        playerInfoRenderer.render(guiGraphics, font)

        seasonInfoRenderer = FancyMultilineTextRenderer(
            lines = seasonInfoLines,
            x = seasonInfoRegionX,
            y = seasonInfoRegionY,
            width = seasonInfoRegionWidth,
            height = seasonInfoRegionHeight,
            scale = textScale,
            lineSpacing = 5,
            alignCenter = false,
            drawShadow = true,
            scrollOffset = seasonScrollOffset
        )
        seasonInfoRenderer.render(guiGraphics, font)

        leaderboardRenderer = FancyMultilineTextRenderer(
            lines = leaderboardLines,
            x = leaderboardRegionX,
            y = leaderboardRegionY,
            width = leaderboardRegionWidth,
            height = leaderboardRegionHeight,
            scale = textScale,
            lineSpacing = 5,
            alignCenter = false,
            drawShadow = true,
            scrollOffset = leaderboardScrollOffset
        )
        leaderboardRenderer.render(guiGraphics, font)

        NotificationManager.render(guiGraphics, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight, font)

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

    private fun requestInfo(type: RequestType) {
        val payload = when (type) {
            RequestType.PLAYER -> RequestPlayerRankPayload(type, format = mode)
            RequestType.LEADERBOARD -> RequestPlayerRankPayload(type, format = mode, extra = leaderboardPage.toString())
            RequestType.SEASON -> RequestPlayerRankPayload(type, format = mode)
        }
        PacketDistributor.sendToServer(payload)
    }

    private fun sendCommand(command: String) {
        minecraft?.player?.connection?.sendCommand(command.removePrefix("/"))
    }

    private fun getLocalizedText(zh: String, en: String): String {
        val lang = minecraft?.languageManager?.selected ?: "en_us"
        return if (lang == "zh_cn") zh else en
    }

    private fun isInRegion(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        return when {
            isInRegion(mouseX, mouseY, leaderboardRegionX, leaderboardRegionY, leaderboardRegionWidth, leaderboardRegionHeight) -> {
                leaderboardScrollOffset = leaderboardRenderer.handleScrollAndReturnOffset(scrollY, font)
                true
            }

            isInRegion(mouseX, mouseY, playerInfoRegionX, playerInfoRegionY, playerInfoRegionWidth, playerInfoRegionHeight) -> {
                playerScrollOffset = playerInfoRenderer.handleScrollAndReturnOffset(scrollY, font)
                true
            }

            isInRegion(mouseX, mouseY, seasonInfoRegionX, seasonInfoRegionY, seasonInfoRegionWidth, seasonInfoRegionHeight) -> {
                seasonScrollOffset = seasonInfoRenderer.handleScrollAndReturnOffset(scrollY, font)
                true
            }

            else -> false
        }
    }

    fun updateInfo(type: RequestType, text: String?) {
        if (text.isNullOrBlank()) {
            if (type == RequestType.LEADERBOARD && leaderboardPage > 1) {
                leaderboardPage--
            }
            when (type) {
                RequestType.PLAYER -> NotificationManager.show(
                    Component.literal(getLocalizedText("§c未找到您的战绩数据。", "§cYour ranked data could not be found."))
                )

                RequestType.SEASON -> NotificationManager.show(
                    Component.literal(getLocalizedText("§c未找到赛季信息。", "§cNo season information found."))
                )

                RequestType.LEADERBOARD -> NotificationManager.show(
                    Component.literal(getLocalizedText("§7暂无更多数据。", "§7No more data available."))
                )
            }
            return
        }

        val lines = text.lines().map { Component.literal(it) }
        when (type) {
            RequestType.PLAYER -> {
                playerInfoLines = lines
                playerScrollOffset = 0
            }

            RequestType.SEASON -> {
                seasonInfoLines = lines
                seasonScrollOffset = 0
            }

            RequestType.LEADERBOARD -> {
                leaderboardLines = lines
                leaderboardScrollOffset = 0
            }
        }
    }
}
