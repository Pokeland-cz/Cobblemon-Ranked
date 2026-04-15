package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max
import kotlin.math.min

class FancyMultilineTextRenderer(
    private val lines: List<Component>,
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int,
    private val scale: Float = 1.0f,
    private val lineSpacing: Int = 4,
    private val alignCenter: Boolean = false,
    private val drawShadow: Boolean = true,
    private var scrollOffset: Int = 0
) {
    fun render(guiGraphics: GuiGraphics, font: Font) {
        guiGraphics.pose().pushPose()

        val finalScale = scale.coerceAtLeast(0.8f)
        guiGraphics.pose().scale(finalScale, finalScale, 1f)

        val wrappedLines = wrappedLines(font)
        val visibleLines = visibleLines(wrappedLines, font)
        val maxLineWidth = wrappedLines.maxOfOrNull { font.width(it) } ?: 0
        val scaledX = (x + (width - maxLineWidth) / 2) / finalScale
        var offsetY = y / finalScale

        visibleLines.forEach { line ->
            val lineWidth = font.width(line)
            val renderX = if (alignCenter) (x + (width - lineWidth) / 2) / finalScale else scaledX
            guiGraphics.drawString(font, line, renderX.toInt(), offsetY.toInt(), 0xFFFFFF, drawShadow)
            offsetY += font.lineHeight + lineSpacing
        }

        drawScrollbar(guiGraphics, font, wrappedLines.size)
        guiGraphics.pose().popPose()
    }

    fun handleScroll(scrollDelta: Double, font: Font): Int {
        val wrappedLines = wrappedLines(font)
        val maxLines = height / (font.lineHeight + lineSpacing)
        val maxOffset = max(0, wrappedLines.size - maxLines)

        if (scrollDelta < 0) {
            scrollOffset = min(scrollOffset + 1, maxOffset)
        } else if (scrollDelta > 0) {
            scrollOffset = max(scrollOffset - 1, 0)
        }

        return scrollOffset
    }

    fun handleScrollAndReturnOffset(scrollDelta: Double, font: Font): Int {
        handleScroll(scrollDelta, font)
        return scrollOffset
    }

    private fun wrappedLines(font: Font): List<FormattedCharSequence> {
        return lines.flatMap { font.split(it, width.coerceAtLeast(20)) }
    }

    private fun visibleLines(wrapped: List<FormattedCharSequence>, font: Font): List<FormattedCharSequence> {
        val maxLines = height / (font.lineHeight + lineSpacing)
        val start = scrollOffset.coerceAtMost(wrapped.size)
        val end = (start + maxLines).coerceAtMost(wrapped.size)
        return wrapped.subList(start, end)
    }

    private fun drawScrollbar(guiGraphics: GuiGraphics, font: Font, totalLines: Int) {
        val lineHeight = font.lineHeight + lineSpacing
        val maxVisibleLines = height / lineHeight
        if (totalLines <= maxVisibleLines) return

        val scrollbarX = x + width - 4
        val scrollbarWidth = 4
        val scrollRatio = scrollOffset.toFloat() / (totalLines - maxVisibleLines).toFloat()
        val handleHeight = (height * maxVisibleLines.toFloat() / totalLines).toInt().coerceAtLeast(10)
        val handleY = y + ((height - handleHeight) * scrollRatio).toInt()

        guiGraphics.fill(scrollbarX, y, scrollbarX + scrollbarWidth, y + height, 0xFF202020.toInt())
        guiGraphics.fill(scrollbarX, handleY, scrollbarX + scrollbarWidth, handleY + handleHeight, 0xFFFFFFFF.toInt())
    }
}
