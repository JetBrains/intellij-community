// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ui.SideBorder
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder


class PyPackagesTable<T : DisplayablePackage>(model: ListTableModel<T>, service: PyPackagingToolWindowService) : JBTable(model) {
  var lastSelectedRow = -1
  init {
    setShowGrid(false)
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    rowHeight = 20
    selectionModel.addListSelectionListener {
      if (selectedRow != -1 && selectedRow != lastSelectedRow) {
        lastSelectedRow = selectedRow
        val pkg = model.items[selectedRow]
        service.packageSelected(pkg)
      }
    }
  }

  override fun clearSelection() {
    lastSelectedRow = -1
    super.clearSelection()
  }

  @Suppress("UNCHECKED_CAST")
  val listModel: ListTableModel<T>
    get() = model as ListTableModel<T>

  var items: List<T>
    get() = listModel.items
    set(value) { listModel.items = value }
}

class PyPackagesTableModel<T : DisplayablePackage> : ListTableModel<T>() {
  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
  override fun getColumnCount(): Int = 2
  override fun getColumnName(column: Int): String = column.toString()
  override fun getColumnClass(columnIndex: Int): Class<*> {
    if (columnIndex == 0) return String::class.java
    if (columnIndex == 1) return Number::class.java
    return Any::class.java
  }

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
    val item = items[rowIndex]
    if (columnIndex == 0) return item.name
    if (columnIndex == 1 && item is InstalledPackage) return item.instance.version
    return null
  }
}

fun boxPanel(init: JPanel.() -> Unit) = object : JPanel() {
  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    alignmentX = LEFT_ALIGNMENT
    init()
  }
}

fun borderPanel(init: JPanel.() -> Unit) = object : JPanel() {
  init {
    layout = BorderLayout(0, 0)
    init()
  }
}

fun headerPanel(label: JLabel, component: JComponent) = object : JPanel() {
  init {
    background = UIUtil.getControlColor()
    layout = BorderLayout()
    border = BorderFactory.createCompoundBorder(SideBorder(UIUtil.getBoundsColor(), SideBorder.BOTTOM), EmptyBorder(0, 5, 0, 5))
    preferredSize = Dimension(preferredSize.width, 25)
    minimumSize = Dimension(minimumSize.width, 25)
    maximumSize = Dimension(maximumSize.width, 25)

    add(label, BorderLayout.WEST)
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        component.isVisible = !component.isVisible
        label.icon = if (component.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
      }
    })
  }
}