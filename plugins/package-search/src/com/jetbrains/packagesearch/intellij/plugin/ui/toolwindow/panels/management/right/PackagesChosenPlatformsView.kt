package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right

import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.asListOfTags
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout

class PackagesChosenPlatformsView {

    private val panelHeight = 26

    private val platformsPanel = RiderUI.boxPanel {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        alignmentY = Component.TOP_ALIGNMENT
    }

    val panel = RiderUI.borderPanel {
        RiderUI.setHeight(this, panelHeight, true)
        border = JBUI.Borders.empty(-8, 6, 0, 6)
        addToCenter(platformsPanel)
    }

    fun refreshUI(meta: PackageSearchDependency?) {
        platformsPanel.removeAll()

        if (meta?.remoteInfo?.mpp == null) {
            hide()
        } else {
            @Suppress("MagicNumber") // Gotta love Swing APIs
            meta.remoteInfo?.platforms?.asListOfTags()?.forEach { tag ->
                platformsPanel.add(RiderUI.createPlatformTag(tag))
                platformsPanel.add(Box.createHorizontalStrut(6))
            }
        }

        panel.updateAndRepaint()
    }

    fun show() {
        panel.isVisible = true
    }

    fun hide() {
        panel.isVisible = false
    }
}
