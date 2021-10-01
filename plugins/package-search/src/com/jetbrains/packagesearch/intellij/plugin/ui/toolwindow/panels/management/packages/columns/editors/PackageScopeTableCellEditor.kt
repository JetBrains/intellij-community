package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors

import com.intellij.ui.components.editors.JBComboBoxTableCellEditorComponent
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AbstractTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.components.ComboBoxTableCellEditorComponent
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ScopeViewModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.colors
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PopupMenuListItemCellRenderer
import java.awt.Component
import javax.swing.JTable

internal object PackageScopeTableCellEditor : AbstractTableCellEditor() {

    private val comboBoxEditor = JBComboBoxTableCellEditorComponent()

    override fun getCellEditorValue(): Any? = comboBoxEditor.editorValue

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component =
        when (val scopeViewModel = value as ScopeViewModel<*>) {
            is ScopeViewModel.InstalledPackage -> {
                val availableScopes = scopeViewModel.packageModel.usageInfo
                    .flatMap { it.availableScopes }

                val scopeViewModels = (availableScopes + scopeViewModel.installedScopes)
                    .distinct()
                    .sorted()
                    .map { scopeViewModel.copy(selectedScope = it) }

                createComboBoxEditor(table, scopeViewModels, scopeViewModel.selectedScope)
            }
            is ScopeViewModel.InstallablePackage -> {
                val scopeViewModels = scopeViewModel.availableScopes
                    .distinct()
                    .sorted()
                    .map { scopeViewModel.copy(selectedScope = it) }

                createComboBoxEditor(table, scopeViewModels, scopeViewModel.selectedScope)
            }
        }.apply {
            table.colors.applyTo(this, isSelected = true)
            setCell(row, column)
        }

    @Suppress("DuplicatedCode")
    private fun createComboBoxEditor(
        table: JTable,
        scopeViewModels: List<ScopeViewModel<*>>,
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
