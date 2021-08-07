package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.intellij.icons.AllIcons
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ActionsColumn.ActionsViewModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.colors
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledEmptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.setUnderlined
import java.util.EventObject
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer

@Suppress("MagicNumber") // Swing dimension constants
internal class PackageActionsTableCellRendererAndEditor(
    private val actionPerformedCallback: (ActionsViewModel) -> Unit
) : AbstractTableCellEditor(), TableCellRenderer {

    private var lastEditorValue: ActionsViewModel? = null

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) = createComponent(table, isSelected, value as? ActionsViewModel)

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): JComponent? {
        check(value is ActionsViewModel) { "The Actions column value must be an ActionsViewModel, but was ${value::class.simpleName}" }
        actionPerformedCallback(value)
        lastEditorValue = value
        table.cellEditor?.stopCellEditing()
        return null // This should cause editing to stop immediately (see JBTable#prepareEditor)
    }

    override fun getCellEditorValue(): Any? = lastEditorValue

    private fun createComponent(
        table: JTable,
        isSelected: Boolean,
        viewModel: ActionsViewModel?
    ): JComponent {
        val isSearchResult = viewModel?.isSearchResult ?: false
        val colors = when {
            isSearchResult -> table.colors.copy(background = PackageSearchUI.ListRowHighlightBackground)
            else -> table.colors
        }

        if (viewModel?.operationType == null) {
            return JPanel().apply {
                colors.applyTo(this, isSelected)
            }
        }

        return JLabel().apply {
            colors.applyTo(this, isSelected)
            isOpaque = true
            horizontalAlignment = SwingConstants.RIGHT
            border = scaledEmptyBorder(right = 10)

            if (viewModel.isHover) {
                setUnderlined()
            }

            foreground = if (isSelected) {
                table.colors.selectionForeground
            } else {
                JBUI.CurrentTheme.Link.Foreground.ENABLED
            }

            if (viewModel.infoMessage != null) {
                icon = AllIcons.General.BalloonInformation
                iconTextGap = 4.scaled()

                toolTipText = viewModel.infoMessage
            }

            text = when (viewModel.operationType) {
                PackageOperationType.SET -> PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.actions.set")
                PackageOperationType.INSTALL -> PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.actions.install")
                PackageOperationType.UPGRADE -> PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.actions.upgrade")
            }
        }
    }

    override fun shouldSelectCell(anEvent: EventObject?): Boolean = false
}
