package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors

import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AbstractTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.components.ComboBoxTableCellEditorComponent
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.colors
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PopupMenuListItemCellRenderer
import java.awt.Component
import javax.swing.JTable

internal object PackageScopeTableCellEditor : AbstractTableCellEditor() {

    private var lastEditor: ComboBoxTableCellEditorComponent<*>? = null

    override fun getCellEditorValue(): Any? = lastEditor?.value

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component {
        val editor = when (val uiPackageModel = value as UiPackageModel<*>) {
            is UiPackageModel.Installed -> {
                val availableScopes = uiPackageModel.packageModel.usageInfo
                    .flatMap { it.availableScopes }

                val scopeViewModels = (availableScopes + uiPackageModel.declaredScopes)
                    .distinct()
                    .sorted()
                    .map { uiPackageModel.copy(selectedScope = it) }

                createComboBoxEditor(table, scopeViewModels, uiPackageModel.selectedScope)
            }
            is UiPackageModel.SearchResult -> {
                val scopeViewModels = uiPackageModel.declaredScopes
                    .distinct()
                    .sorted()
                    .map { uiPackageModel.copy(selectedScope = it) }

                createComboBoxEditor(table, scopeViewModels, uiPackageModel.selectedScope)
            }
        }.apply {
            table.colors.applyTo(this, isSelected = true)
            setCell(row, column)
        }

        lastEditor = editor
        return editor
    }

    @Suppress("DuplicatedCode")
    private fun createComboBoxEditor(
        table: JTable,
        scopeViewModels: List<UiPackageModel<*>>,
        selectedScope: PackageScope
    ): ComboBoxTableCellEditorComponent<*> {
        require(table is JBTable) { "The packages list table is expected to be a JBTable, but was a ${table::class.qualifiedName}" }

        val selectedViewModel = scopeViewModels.find { it.selectedScope == selectedScope }
        val cellRenderer = PopupMenuListItemCellRenderer(selectedViewModel, table.colors) { it.selectedScope.displayName }

        return ComboBoxTableCellEditorComponent(table, cellRenderer).apply {
            options = scopeViewModels
            value = selectedViewModel
            isShowBelowCell = false
            isForcePopupMatchCellWidth = false
        }
    }
}
