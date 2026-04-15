package cn.kurt6.cobblemon_ranked.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import kotlin.math.cos
import kotlin.math.sin

abstract class RankedBaseScreen(title: Component) : Screen(title) {
    protected lateinit var backgroundTexture: ResourceLocation
    protected val particles = mutableListOf<Particle>()
    protected val random = RandomSource.create()
    protected var animationTime = 0f

    protected var uiX = 0
    protected var uiY = 0
    protected var uiWidth = 0
    protected var uiHeight = 0

    override fun init() {
        super.init()
        loadTextures()

        uiWidth = width
        uiHeight = height
        uiX = 0
        uiY = 0

        particles.clear()
        repeat(50) {
            particles += Particle(
                x = random.nextDouble() * uiWidth,
                y = random.nextDouble() * uiHeight,
                size = random.nextDouble() * 3 + 1,
                speed = random.nextDouble() * 20 + 10,
                angle = random.nextDouble() * Math.PI * 2,
                color = ((0x60 + random.nextInt(0x40)) shl 24) or
                    (random.nextInt(0x40) shl 16) or
                    (random.nextInt(0xA0) shl 8) or
                    0xFF
            )
        }
    }

    private fun loadTextures() {
        backgroundTexture = ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "textures/gui/ranked_bg.png")
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        animationTime += partialTick
        blit(guiGraphics, backgroundTexture, uiX, uiY, uiWidth, uiHeight)
        guiGraphics.fill(uiX, uiY, uiX + uiWidth, uiY + uiHeight, 0x90101010.toInt())

        particles.forEach {
            it.update(partialTick)
            guiGraphics.fill(
                it.x.toInt(),
                it.y.toInt(),
                (it.x + it.size).toInt(),
                (it.y + it.size).toInt(),
                it.color
            )
        }

        guiGraphics.fillGradient(uiX, uiY, uiX + uiWidth, uiY + 40, 0x80000000.toInt(), 0x00000000)
        guiGraphics.fillGradient(uiX, uiY + uiHeight - 40, uiX + uiWidth, uiY + uiHeight, 0x00000000, 0x80000000.toInt())
    }

    override fun onClose() {
        particles.clear()
        super.onClose()
    }

    protected fun blit(
        guiGraphics: GuiGraphics,
        texture: ResourceLocation,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        guiGraphics.blit(texture, x, y, width, height, 0f, 0f, width, height, width, height)
    }

    protected fun langSuffix(): String {
        val languageCode = minecraft?.languageManager?.selected ?: "en_us"
        return if (languageCode.lowercase().startsWith("zh")) "zh" else "en"
    }

    protected fun drawFooter(guiGraphics: GuiGraphics) {
        guiGraphics.drawString(font, Component.literal("By Kurt"), width - 40, height - 12, 0xAAAAAA, false)
    }

    protected inner class Particle(
        var x: Double,
        var y: Double,
        var size: Double,
        var speed: Double,
        var angle: Double,
        var color: Int
    ) {
        fun update(delta: Float) {
            val safeDelta = delta.coerceIn(0f, 0.1f)
            x += cos(angle) * speed * safeDelta
            y += sin(angle) * speed * safeDelta

            if (x < -50) x = (uiWidth + 50).toDouble()
            if (x > uiWidth + 50) x = -50.0
            if (y < -50) y = (uiHeight + 50).toDouble()
            if (y > uiHeight + 50) y = -50.0
        }
    }
}
