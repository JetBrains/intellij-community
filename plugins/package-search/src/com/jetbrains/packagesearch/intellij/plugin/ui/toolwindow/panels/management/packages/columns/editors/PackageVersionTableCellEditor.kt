package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors

import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AbstractTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.components.ComboBoxTableCellEditorComponent
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.colors
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PopupMenuListItemCellRenderer
import java.awt.Component
import javax.swing.JTable

internal class PackageVersionTableCellEditor : AbstractTableCellEditor() {

    private var lastEditor: ComboBoxTableCellEditorComponent<*>? = null

    private var onlyStable = false

    fun updateData(onlyStable: Boolean) {
        this.onlyStable = onlyStable
    }

    override fun getCellEditorValue(): Any? = lastEditor?.value

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component {
        val viewModel = value as UiPackageModel<*>

        val versionViewModels = when (viewModel) {
            is UiPackageModel.Installed -> viewModel.sortedVersions.map { viewModel.copy(selectedVersion = it) }
            is UiPackageModel.SearchResult -> viewModel.sortedVersions.map { viewModel.copy(selectedVersion = it) }
        }

        val editor = createComboBoxEditor(table, versionViewModels, viewModel.selectedVersion.originalVersion)
            .apply {
                table.colors.applyTo(this, isSelected = true)
                setCell(row, column)
            }

        lastEditor = editor
        return editor
    }

    @Suppress("DuplicatedCode")
    private fun createComboBoxEditor(
        table: JTable,
        versionViewModels: List<UiPackageModel<*>>,
        selectedVersion: PackageVersion
    ): ComboBoxTableCellEditorComponent<*> {
        require(table is JBTable) { "The packages list table is expected to be a JBTable, but was a ${table::class.qualifiedName}" }

        val selectedViewModel = versionViewModels.find { it.selectedVersion == selectedVersion }
        val cellRenderer = PopupMenuListItemCellRenderer(selectedViewModel, table.colors) { it.selectedVersion.displayName }

        return ComboBoxTableCellEditorComponent(table, cellRenderer).apply {
            isShowBelowCell = false
            isForcePopupMatchCellWidth = false
            options = versionViewModels
            value = selectedViewModel
        }
    }
}
