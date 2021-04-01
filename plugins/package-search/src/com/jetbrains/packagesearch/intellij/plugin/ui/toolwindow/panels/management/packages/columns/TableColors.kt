package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import java.awt.Color
import javax.swing.JComponent
import javax.swing.JTable

internal data class TableColors(
    val selectionBackground: Color,
    val selectionForeground: Color,
    val background: Color,
    val foreground: Color
) {

    fun applyTo(component: JComponent, isSelected: Boolean) {
        component.background = if (isSelected) selectionBackground else background
        component.foreground = if (isSelected) selectionForeground else foreground
    }
}

internal val JTable.colors: TableColors
    get() = TableColors(selectionBackground, selectionForeground, background, foreground)
