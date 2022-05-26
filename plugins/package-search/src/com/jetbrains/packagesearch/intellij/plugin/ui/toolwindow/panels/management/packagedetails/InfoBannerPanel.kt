package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.HorizontalLayout
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

@Suppress("MagicNumber") // Swing dimension constants
class InfoBannerPanel(
    @Nls text: String = "",
    backgroundColor: JBColor = PackageSearchUI.InfoBannerBackground,
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
        background = backgroundColor
        PackageSearchUI.setHeight(this, 28)

        layout = HorizontalLayout(16.scaled())
        border = emptyBorder(vSize = 6, hSize = 12)

        this.text = text
        this.icon = icon

        add(bannerLabel)
    }
}
