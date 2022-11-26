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
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.ui.components.ComboBoxTableCellEditorComponent
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PopupMenuListItemCellRenderer
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.computeColors
import java.awt.Color
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
                val colors = computeColors(isSelected = true, isHover = false, isSearchResult = viewModel is UiPackageModel.SearchResult)
                colors.applyTo(this)
                setCell(row, column)
            }

        lastEditor = editor
        return editor
    }

    private fun createComboBoxEditor(
        table: JTable,
        versionViewModels: List<UiPackageModel<*>>,
        selectedVersion: PackageVersion
    ): ComboBoxTableCellEditorComponent<*> {
        require(table is JBTable) { "The packages list table is expected to be a JBTable, but was a ${table::class.qualifiedName}" }

        val selectedViewModel = versionViewModels.find { it.selectedVersion == selectedVersion }
        val cellRenderer = PopupMenuListItemCellRenderer(selectedViewModel) { it.selectedVersion.displayName }

        return ComboBoxTableCellEditorComponent(table, cellRenderer).apply {
            isShowBelowCell = false
            isForcePopupMatchCellWidth = false
            setOptions(*versionViewModels.toTypedArray())
            value = selectedViewModel
        }
    }
}
