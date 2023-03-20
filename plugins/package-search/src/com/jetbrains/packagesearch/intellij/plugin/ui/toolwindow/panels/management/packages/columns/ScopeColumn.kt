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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors.PackageScopeTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageScopeTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class ScopeColumn(
    private val scopeSetter: (uiPackageModel: UiPackageModel<*>, newScope: PackageScope) -> Unit
) : ColumnInfo<PackagesTableItem<*>, PackagesTableItem<*>>(
    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.scope")
) {

    override fun getRenderer(item: PackagesTableItem<*>): TableCellRenderer = PackageScopeTableCellRenderer

    override fun getEditor(item: PackagesTableItem<*>): TableCellEditor = PackageScopeTableCellEditor

    override fun isCellEditable(item: PackagesTableItem<*>?) = true

    override fun valueOf(item: PackagesTableItem<*>) = item

    override fun setValue(item: PackagesTableItem<*>?, value: PackagesTableItem<*>?) {
        if (value == null || item == null) return
        if (value.uiPackageModel.selectedScope == item.uiPackageModel.selectedScope) return

        scopeSetter(item.uiPackageModel, value.uiPackageModel.selectedScope)
    }
}
