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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

@Suppress("MagicNumber") // Swing dimension constants
class InfoBannerPanel(
    @Nls text: String = "",
    icon: Icon? = AllIcons.General.BalloonInformation
) : JPanel() {

    private val bannerLabel = JLabel()

    @get:Nls
    @set:Nls
    var text: String
        get() = bannerLabel.text
        set(@Nls value) {
            bannerLabel.text = value
        }

    var icon: Icon?
        get() = bannerLabel.icon
        set(value) {
            bannerLabel.icon = value
        }

    init {
        background = PackageSearchUI.Colors.InfoBanner.background

        PackageSearchUI.setHeight(this, 28)

        layout = BorderLayout()
        bannerLabel.border = emptyBorder(vSize = 6, hSize = 12)

        this.text = text
        this.icon = icon

        add(bannerLabel, BorderLayout.NORTH)
    }

    override fun getBorder() = JBUI.Borders.customLineBottom(PackageSearchUI.Colors.InfoBanner.border)

    override fun getBackground() = PackageSearchUI.Colors.InfoBanner.background
}
