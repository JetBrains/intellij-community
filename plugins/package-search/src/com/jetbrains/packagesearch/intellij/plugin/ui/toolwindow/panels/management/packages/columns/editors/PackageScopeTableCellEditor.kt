/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors

import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AbstractTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.components.ComboBoxTableCellEditorComponent
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PopupMenuListItemCellRenderer
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.computeColors
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

                createComboBoxEditor(table, scopeViewModels, item.uiPackageModel.selectedScope, isSelected, false)
            }
            is PackagesTableItem.InstallablePackage -> {
                val scopeViewModels = item.allScopes
                    .map { item.copy(uiPackageModel = item.uiPackageModel.copy(selectedScope = it)) }

                createComboBoxEditor(table, scopeViewModels, item.uiPackageModel.selectedScope, isSelected, true)
            }
        }.apply {
            setCell(row, column)
        }

        lastEditor = editor
        return editor
    }

    private fun createComboBoxEditor(
        table: JTable,
        scopeViewModels: List<PackagesTableItem<*>>,
        selectedScope: PackageScope,
        isSelected: Boolean,
        isSearchResult: Boolean
    ): ComboBoxTableCellEditorComponent<*> {
        require(table is JBTable) { "The packages list table is expected to be a JBTable, but was a ${table::class.qualifiedName}" }

        val selectedViewModel = scopeViewModels.find { it.uiPackageModel.selectedScope == selectedScope }
        val cellRenderer = PopupMenuListItemCellRenderer(selectedViewModel) {
            it.uiPackageModel.selectedScope.displayName
        }

        val colors = computeColors(isSelected, isHover = false, isSearchResult)
        return ComboBoxTableCellEditorComponent(table, cellRenderer).apply {
            setOptions(*scopeViewModels.toTypedArray())
            value = selectedViewModel
            isShowBelowCell = false
            isForcePopupMatchCellWidth = false
            colors.applyTo(this)
        }
    }
}
