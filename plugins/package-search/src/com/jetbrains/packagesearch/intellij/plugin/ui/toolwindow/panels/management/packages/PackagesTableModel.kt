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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.util.ui.ListTableModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ActionsColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.NameColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ScopeColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.VersionColumn

internal class PackagesTableModel(
    val nameColumn: NameColumn,
    val scopeColumn: ScopeColumn,
    val versionColumn: VersionColumn,
    val actionsColumn: ActionsColumn
) : ListTableModel<PackagesTableItem<*>>(nameColumn, scopeColumn, versionColumn, actionsColumn) {

    val columns by lazy { arrayOf(nameColumn, scopeColumn, versionColumn, actionsColumn) }

    override fun getRowCount() = items.size
    override fun getColumnCount() = columns.size
    override fun getColumnClass(columnIndex: Int): Class<out Any> = columns[columnIndex].javaClass
}
