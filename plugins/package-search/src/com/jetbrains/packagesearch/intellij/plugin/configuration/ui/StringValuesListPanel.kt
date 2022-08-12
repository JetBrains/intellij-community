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

package com.jetbrains.packagesearch.intellij.plugin.configuration.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

class StringValuesListPanel(initialValues: List<String>) : BorderLayoutPanel() {

    private val tableModel = DefaultTableModel(
        arrayOf(initialValues.toTypedArray()),
        arrayOf(PackageSearchBundle.message("packagesearch.configuration.gradle.configurations.columnName"))
    )

    private val table = object : JBTable(tableModel) {

        init {
            visibleRowCount = 5
            setTableHeader(null)
        }
    }

    val component: JComponent

    var values
        get() = (tableModel.dataVector.firstOrNull().orEmpty().toList() as List<String>)
            .filter { it.isNotBlank() }
            .map { it.trim() }
        set(value) {
            tableModel.dataVector.clear()
            for (row in value) {
                tableModel.addRow(arrayOf(row))
            }
        }

    init {
        val decoratedList = ToolbarDecorator.createDecorator(table)
            .setAddAction {
                tableModel.addRow(arrayOf(""))
                table.changeSelection(tableModel.dataVector.lastIndex, 0, false, false)
                ApplicationManager.getApplication().invokeLater {
                    if (table.editCellAt(table.selectedRow, table.selectedColumn)) {
                        table.editorComponent.requestFocusInWindow()
                    }
                }
            }
            .setRemoveAction {
                table.selectedRows.reversed()
                    .forEach { tableModel.removeRow(it) }
            }
            .setEditAction { table.editCellAt(table.selectedRow, table.selectedColumn) }
            .disableUpDownActions()
            .createPanel()

        component = decoratedList
    }
}
