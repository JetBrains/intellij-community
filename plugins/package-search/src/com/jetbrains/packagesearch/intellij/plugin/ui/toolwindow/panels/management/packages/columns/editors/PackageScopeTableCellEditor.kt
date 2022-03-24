package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors

import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AbstractTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.components.ComboBoxTableCellEditorComponent
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.colors
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PopupMenuListItemCellRenderer
import java.awt.Component
import javax.swing.JTable

internal object PackageScopeTableCellEditor : AbstractTableCellEditor() {

    private var lastEditor: ComboBoxTableCellEditorComponent<*>? = null

    override fun getCellEditorValue(): Any? = lastEditor?.value

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component {
        val editor = when (val item = value as PackagesTableItem<*>) {
            is PackagesTableItem.InstalledPackage -> {
                val scopeViewModels = item.allScopes
                    .map { item.copy(uiPackageModel = item.uiPackageModel.copy(selectedScope = it)) }

                createComboBoxEditor(table, scopeViewModels, item.uiPackageModel.selectedScope)
            }
            is PackagesTableItem.InstallablePackage -> {
                val scopeViewModels = item.allScopes
                    .map { item.copy(uiPackageModel = item.uiPackageModel.copy(selectedScope = it)) }

                createComboBoxEditor(table, scopeViewModels, item.uiPackageModel.selectedScope)
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
        scopeViewModels: List<PackagesTableItem<*>>,
        selectedScope: PackageScope
    ): ComboBoxTableCellEditorComponent<*> {
        require(table is JBTable) { "The packages list table is expected to be a JBTable, but was a ${table::class.qualifiedName}" }

        val selectedViewModel = scopeViewModels.find { it.uiPackageModel.selectedScope == selectedScope }
        val cellRenderer = PopupMenuListItemCellRenderer(selectedViewModel, table.colors) {
            it.uiPackageModel.selectedScope.displayName
        }

        return ComboBoxTableCellEditorComponent(table, cellRenderer).apply {
            options = scopeViewModels
            value = selectedViewModel
            isShowBelowCell = false
            isForcePopupMatchCellWidth = false
        }
    }
}
