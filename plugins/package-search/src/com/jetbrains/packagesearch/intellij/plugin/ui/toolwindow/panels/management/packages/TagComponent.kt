/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ui.RelativeFont
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.ScaledPixels
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import javax.swing.JLabel

@Suppress("MagicNumber") // Swing dimension constants
class TagComponent(@Nls name: String) : JLabel() {

    init {
        isOpaque = false
        border = emptyBorder(vSize = 1, hSize = 8)
        RelativeFont.TINY.install(this)
        text = name
        toolTipText = PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.tooltip")
        GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAAHintForSwingComponent())
        background = PackageSearchUI.Colors.PackagesTable.Tag.background(isSelected = false, isHover = false)
        foreground = PackageSearchUI.Colors.PackagesTable.Tag.foreground(isSelected = false, isHover = false)
    }

    @ScaledPixels
    var tagDiameterPx: Int = 4.scaled()
        set(value) {
            require(value >= 0) { "The diameter must be equal to or greater than zero." }
            field = JBUIScale.scale(value)
        }

    var isSelected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun paintComponent(g: Graphics) {
        val config = GraphicsUtil.setupRoundedBorderAntialiasing(g)
        g.color = background
        g.fillRoundRect(0, 0, width, height, tagDiameterPx, tagDiameterPx)
        config.restore()
        super.paintComponent(g)
    }
}
