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

package com.jetbrains.packagesearch.intellij.plugin.ui.components

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.TableUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.colors
import com.jetbrains.packagesearch.intellij.plugin.ui.util.bottom
import com.jetbrains.packagesearch.intellij.plugin.ui.util.left
import com.jetbrains.packagesearch.intellij.plugin.ui.util.top
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.ListCellRenderer
import javax.swing.event.TableModelEvent

// Adapted from JBComboBoxTableCellEditorComponent
internal class ComboBoxTableCellEditorComponent<T : Any>(
    private val table: JBTable,
    private var cellRenderer: ListCellRenderer<T>
) : JBLabel() {

    var options: Iterable<T> = emptyList()

    var isForcePopupMatchCellWidth = true
    var isShowBelowCell = true

    var value: T? = null

    private var row = 0
    private var column = 0

    private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<ActionListener>()

    init {
        isOpaque = true
        background = table.colors.selectionBackground
    }

    fun setCell(row: Int? = null, column: Int? = null) {
        if (row != null) this.row = row
        if (column != null) this.column = column
    }

    override fun addNotify() {
        super.addNotify()
        initAndShowPopup()
    }

    private fun initAndShowPopup() {
        val cellRect = table.getCellRect(row, column, true)
        val surrendersFocusOnKeystrokeOldValue = table.surrendersFocusOnKeyStroke()

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(options.toList())
            .setRenderer(cellRenderer)
            .setItemChosenCallback { selectedItem ->
                value = selectedItem
                val event = ActionEvent(this, ActionEvent.ACTION_PERFORMED, "elementChosen")
                for (listener in listeners) {
                    listener.actionPerformed(event)
                }
                TableUtil.stopEditing(table)
                table.setValueAt(value, row, column) // on Mac getCellEditorValue() is called before myValue is set.
                table.tableChanged(TableModelEvent(table.model, row)) // force repaint
            }
            .setCancelCallback {
                TableUtil.stopEditing(table)
                true
            }
            .addListener(object : JBPopupListener {
                override fun beforeShown(event: LightweightWindowEvent) {
                    table.surrendersFocusOnKeystroke = false
                }

                override fun onClosed(event: LightweightWindowEvent) {
                    table.surrendersFocusOnKeystroke = surrendersFocusOnKeystrokeOldValue
                }
            })
            .setMinSize(if (isForcePopupMatchCellWidth) Dimension(cellRect.size.getWidth().toInt(), -1) else null)
            .createPopup()

        val popupLocation = Point(cellRect.left, if (isShowBelowCell) cellRect.bottom else cellRect.top)
        popup.show(RelativePoint(table, popupLocation))
    }

    fun addActionListener(listener: ActionListener) {
        listeners.add(listener)
    }
}
