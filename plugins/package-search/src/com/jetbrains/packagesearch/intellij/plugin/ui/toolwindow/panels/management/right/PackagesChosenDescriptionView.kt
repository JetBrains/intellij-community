package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.normalizeSpace
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import java.awt.Component

class PackagesChosenDescriptionView {

    private val panelHeight = 26

    private val packageDescription = JBLabel("")
    private val packageDescriptionPanel = RiderUI.boxPanel {
        alignmentX = Component.LEFT_ALIGNMENT

        add(packageDescription)
    }

    val panel = RiderUI.borderPanel {
        RiderUI.setHeight(this, panelHeight, true)
        border = JBUI.Borders.empty(0, 6, 0, 6)
        addToCenter(packageDescriptionPanel)
    }

    fun refreshUI(meta: PackageSearchDependency?) {
        var text = meta?.remoteInfo?.description
        if (text.isNullOrEmpty() || text.equals("null", true)) text = meta?.identifier ?: ""

        packageDescription.text = text.normalizeSpace()

        panel.updateAndRepaint()
    }

    fun show() {
        panel.isVisible = true
    }

    fun hide() {
        panel.isVisible = false
    }
}
