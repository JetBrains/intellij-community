package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right

import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.normalizeSpace
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderColor
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchDependency
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import org.apache.commons.lang3.StringUtils

class PackagesChosenTitleView {
    private val idLabel = RiderUI.createBigLabel()
    private val identifierLabel = RiderUI.createLabel().apply {
        foreground = RiderColor(Color.GRAY, Color.GRAY)
        border = JBUI.Borders.empty(4, 0, 0, 0)
    }

    val panel = RiderUI.headerPanel {
        RiderUI.setHeight(this, RiderUI.MediumHeaderHeight)
        border = JBUI.Borders.empty(0, 8, 0, 8)

        add(RiderUI.headerPanel {
            border = JBEmptyBorder(0, 1, 0, 0)
            layout = FlowLayout(FlowLayout.LEFT)

            add(idLabel)
            add(identifierLabel)
        }, BorderLayout.WEST)
    }

    fun refreshUI(meta: PackageSearchDependency) {
        if (meta.remoteInfo?.name != null && meta.remoteInfo?.name != meta.identifier) {
            idLabel.text = meta.remoteInfo?.name.normalizeSpace()
            identifierLabel.text = meta.identifier
        } else {
            idLabel.text = meta.identifier
            identifierLabel.text = ""
        }
    }

    fun hide() {
        idLabel.text = ""
        identifierLabel.text = ""
    }
}
