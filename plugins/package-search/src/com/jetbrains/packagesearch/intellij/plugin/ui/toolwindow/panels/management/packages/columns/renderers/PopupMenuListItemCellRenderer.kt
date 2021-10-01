package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.intellij.ide.ui.AntialiasingType
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GraphicsUtil
import com.jetbrains.packagesearch.PackageSearchIcons
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.TableColors
import java.awt.Component
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState
import javax.accessibility.AccessibleStateSet
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList

internal class PopupMenuListItemCellRenderer<T>(
    private val selectedValue: T?,
    private val colors: TableColors,
    private val itemLabelRenderer: (T) -> String = { it.toString() }
) : DefaultListCellRenderer() {

    private val selectedIcon = PackageSearchIcons.Checkmark
    private val emptyIcon = EmptyIcon.create(selectedIcon.iconWidth)
    private var currentItemIsSelected = false

    override fun getListCellRendererComponent(list: JList<*>, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
        @Suppress("UNCHECKED_CAST") // It's ok to crash if this isn't true
        val item = value as T

        val itemLabel = itemLabelRenderer(item) + " " // The spaces are to compensate for the lack of padding in the label (yes, I know, it's a hack)
        val label = super.getListCellRendererComponent(list, itemLabel, index, isSelected, cellHasFocus) as JLabel
        label.font = list.font
        colors.applyTo(label, isSelected)

        currentItemIsSelected = item === selectedValue

        label.icon = if (currentItemIsSelected) selectedIcon else emptyIcon

        GraphicsUtil.setAntialiasingType(label, AntialiasingType.getAAHintForSwingComponent())
        return label
    }

    override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
            accessibleContext = AccessibleRenderer(currentItemIsSelected)
        }
        return accessibleContext
    }

    private inner class AccessibleRenderer(
        private val isSelected: Boolean
    ) : AccessibleJLabel() {

        override fun getAccessibleRole(): AccessibleRole = AccessibleRole.CHECK_BOX

        override fun getAccessibleStateSet(): AccessibleStateSet {
            val set = super.getAccessibleStateSet()
            if (isSelected) {
                set.add(AccessibleState.CHECKED)
            }
            return set
        }
    }
}
