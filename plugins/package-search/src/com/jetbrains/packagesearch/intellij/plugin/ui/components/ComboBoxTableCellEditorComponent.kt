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

import com.intellij.ui.components.editors.JBComboBoxTableCellEditorComponent
import com.intellij.ui.table.JBTable
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import javax.swing.ListCellRenderer

internal class ComboBoxTableCellEditorComponent<T : Any>(
    table: JBTable,
    cellRenderer: ListCellRenderer<T>
) : JBComboBoxTableCellEditorComponent(table) {

    var isForcePopupMatchCellWidth
        get() = myWide
        set(value) {setWide(value)}

    var isShowBelowCell = true

    var value: T? = null

    private var myRow = 0
    private var myColumn = 0

    init {
        isOpaque = true
        background = PackageSearchUI.Colors.PackagesTable.background(isSelected = false, isHover = false)
        setRenderer(cellRenderer)
    }

    fun setCell(row: Int? = null, column: Int? = null) {
        if (row != null)  setRow(row)
        if (column != null) setColumn(column)
    }

    override fun setRow(row: Int) {
        super.setRow(row)
        myRow = row
    }

    override fun setColumn(column: Int) {
        super.setColumn(column)
        myColumn = column
    }
//
//    override fun addNotify() {
//        super.addNotify()
//        initAndShowPopup()
//    }
//
//    private fun initAndShowPopup() {
//        val cellRect = table.getCellRect(myRow, column, true)
//        val surrendersFocusOnKeystrokeOldValue = table.surrendersFocusOnKeyStroke()
//
//        val popup = JBPopupFactory.getInstance()
//            .createPopupChooserBuilder(options.toList())
//            .setRenderer(cellRenderer)
//            .setItemChosenCallback { selectedItem ->
//                value = selectedItem
//                TableUtil.stopEditing(table)
//                table.setValueAt(value, myRow, column) // on Mac getCellEditorValue() is called before myValue is set.
//                table.tableChanged(TableModelEvent(table.model, myRow)) // force repaint
//            }
//            .setCancelCallback {
//                TableUtil.stopEditing(table)
//                true
//            }
//            .addListener(object : JBPopupListener {
//                override fun beforeShown(event: LightweightWindowEvent) {
//                    table.surrendersFocusOnKeystroke = false
//                }
//
//                override fun onClosed(event: LightweightWindowEvent) {
//                    table.surrendersFocusOnKeystroke = surrendersFocusOnKeystrokeOldValue
//                }
//            })
//            .setMinSize(if (isForcePopupMatchCellWidth) Dimension(cellRect.size.getWidth().toInt(), -1) else null)
//            .createPopup()
//
//        val popupLocation = Point(cellRect.left, if (isShowBelowCell) cellRect.bottom else cellRect.top)
//        popup.show(RelativePoint(table, popupLocation))
//    }
}
