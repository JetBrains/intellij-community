package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repository
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.Property
import java.awt.Color

class RepositoryColorManager(lifetime: Lifetime, private val remoteRepositories: Property<List<V2Repository>>) {

    companion object {
        private val sourceColors =
            arrayOf(JBColor.BLUE, JBColor.GREEN, JBColor.ORANGE, JBColor.CYAN, JBColor.YELLOW, JBColor.MAGENTA, JBColor.PINK)

        private val defaultRootColor: Color get() = UIUtil.getTableBackground()

        fun getBackgroundColor(baseRootColor: Color) = JBColor {
            @Suppress("MagicNumber")
            ColorUtil.mix(baseRootColor, UIUtil.getTableBackground(), 0.75)
        }

        fun getIndicatorColor(baseRootColor: Color) = JBColor {
            if (UIUtil.isUnderDarcula()) baseRootColor else ColorUtil.darker(ColorUtil.softer(baseRootColor), 1)
        }
    }

    private val colorMap = mutableMapOf<String, Color>()

    init {
        generatePalette()
        remoteRepositories.advise(lifetime) {
            generatePalette()
        }
    }

    private fun generatePalette() {
        val repositories = remoteRepositories.value

        colorMap.clear()
        var i = 0
        for (repository in repositories) {
            val color: Color
            if (i >= sourceColors.size) {
                val balance = (i / sourceColors.size).toDouble() / (repositories.size / sourceColors.size)
                val mix = ColorUtil.mix(sourceColors[i % sourceColors.size], sourceColors[(i + 1) % sourceColors.size], balance)
                val tones = (Math.abs(balance - 0.5) * 2.0 * (repositories.size / sourceColors.size).toDouble() + 1).toInt()
                color = JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones))
            } else {
                color = sourceColors[i]
            }
            i++
            colorMap[repository.id] = color
        }
    }

    fun getColor(repository: V2Repository) = colorMap[repository.id] ?: defaultRootColor
}
