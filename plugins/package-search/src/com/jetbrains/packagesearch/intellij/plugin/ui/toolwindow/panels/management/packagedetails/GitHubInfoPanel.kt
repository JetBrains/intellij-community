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
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.compensateForHighlightableComponentMarginLeft
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.noInsets
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.ui.util.skipInvisibleComponents
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import javax.swing.JPanel

@Suppress("MagicNumber") // Swing dimension constants
internal class GitHubInfoPanel : JPanel() {

    private val linkLabel = PackageSearchUI.createLabelWithLink()

    private val starsLabel = PackageSearchUI.createLabel {
        icon = AllIcons.Plugins.Rating
        iconTextGap = 3.scaled()
        foreground = PackageSearchUI.getTextColorSecondary()
    }

    var url: String?
        get() = linkLabel.url
        set(value) {
            linkLabel.url = value
        }

    var stars: Int? = null
        set(value) {
            starsLabel.isVisible = value != null
            starsLabel.text = formatStarsForDisplay(value)
            starsLabel.updateAndRepaint()
        }

    var text: String?
        @Nls get() = linkLabel.text
        set(@Nls value) {
            linkLabel.setDisplayText(value)
        }

    @Nls
    private fun formatStarsForDisplay(stars: Int?): String =
        when (stars) {
            null -> "0"
            in 0..999 -> stars.toString()
            in 1_000..9_999 -> PackageSearchBundle.message("packagesearch.ui.util.numberWithThousandsSymbol", String.format("%.1f", stars / 1000f))
            in 10_000..999_999 -> PackageSearchBundle.message("packagesearch.ui.util.numberWithThousandsSymbol", stars / 1000)
            else -> String.format("%dm", stars / 1_000_000f)
        }

    init {
        background = PackageSearchUI.Colors.panelBackground
        border = emptyBorder(0)
        layout = MigLayout(
            LC().skipInvisibleComponents()
                .noInsets()
                .gridGap("${8 - 2}", "0"), // Compensate for the -2 pad below
            AC().grow(),
            AC().fill()
        )
        linkLabel.urlClickedListener = {
            PackageSearchEventsLogger.logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.GitHub)
        }
        add(linkLabel, CC().compensateForHighlightableComponentMarginLeft())
        add(starsLabel)
    }
}
