// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ui.JBColor
import com.intellij.ui.RelativeFont
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.util.ScaledPixels
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import javax.swing.JLabel

@Suppress("MagicNumber") // Swing dimension constants
class TagComponent(@Nls name: String) : JLabel() {

  init {
    foreground = JBColor.namedColor("Plugins.tagForeground", JBColor(0x808080, 0x808080))
    background = JBColor.namedColor("Plugins.tagBackground", JBColor(0xE8E8E8, 0xE8E8E8))
    isOpaque = false
    border = emptyBorder(vSize = 1, hSize = 8)
    RelativeFont.TINY.install(this)
    text = name
    toolTipText = PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.tooltip")
    GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAAHintForSwingComponent())
  }

    @ScaledPixels
    var tagDiameterPx: Int = 4.scaled()
        set(value) {
            require(value >= 0) { "The diameter must be equal to or greater than zero." }
            field = JBUIScale.scale(value)
        }

    override fun paintComponent(g: Graphics) {
        val config = GraphicsUtil.setupRoundedBorderAntialiasing(g)
        g.color = background
        g.fillRoundRect(0, 0, width, height, tagDiameterPx, tagDiameterPx)
        config.restore()
        super.paintComponent(g)
    }
}
