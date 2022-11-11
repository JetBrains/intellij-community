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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.intellij.icons.AllIcons
import com.intellij.ui.hover.TableHoverListener
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ActionsColumn.ActionViewModel
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import java.util.EventObject
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer

@Suppress("MagicNumber") // Swing dimension constants
internal class PackageActionsTableCellRendererAndEditor(
    private val actionPerformedCallback: (ActionViewModel) -> Unit
) : AbstractTableCellEditor(), TableCellRenderer {

    private var lastEditorValue: ActionViewModel? = null

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) = createRendererComponent(
        isSelected = isSelected,
        isHover = TableHoverListener.getHoveredRow(table) == row,
        viewModel = value as? ActionViewModel
    )

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): JComponent? {
        check(value is ActionViewModel) { "The Actions column value must be an ActionsViewModel, but was ${value::class.simpleName}" }
        actionPerformedCallback(value)
        lastEditorValue = value
        table.cellEditor?.stopCellEditing()
        return null // This should cause editing to stop immediately (see JBTable#prepareEditor)
    }

    override fun getCellEditorValue(): Any? = lastEditorValue

    private fun createRendererComponent(
        isSelected: Boolean,
        isHover: Boolean,
        viewModel: ActionViewModel?
    ): JComponent {
        val isSearchResult = viewModel?.isSearchResult ?: false

        return JLabel().apply {
            isOpaque = true

            val colors = computeColors(isSelected, isHover, isSearchResult)

            background = colors.background
            foreground = if (isSelected) {
                colors.foreground
            } else {
                JBUI.CurrentTheme.Link.Foreground.ENABLED
            }

            if (viewModel?.operationType != null) {
                horizontalAlignment = SwingConstants.RIGHT
                border = emptyBorder(right = 10)

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
    }

    override fun shouldSelectCell(anEvent: EventObject?): Boolean = false
}
