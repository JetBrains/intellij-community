// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SideBorder
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.repository.PyPackageRepository
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer


internal class PyPackagesTable<T : DisplayablePackage>(model: ListTableModel<T>, service: PyPackagingToolWindowService, tablesView: PyPackagingTablesView) : JBTable(model) {
  private var lastSelectedRow = -1
  init {
    setShowGrid(false)
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    val column = columnModel.getColumn(1)
    column.maxWidth = 100
    column.resizable = false
    border = SideBorder(UIUtil.getBoundsColor(), SideBorder.BOTTOM)
    rowHeight = 20

    initCrossNavigation(service, tablesView)

    selectionModel.addListSelectionListener {
      if (selectedRow != -1 && selectedRow != lastSelectedRow) {
        lastSelectedRow = selectedRow
        tablesView.requestSelection(this)
        val pkg = model.items[selectedRow]
        if (pkg !is ExpandResultNode) service.packageSelected(pkg)
      }
    }

    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        val pkg = model.items[selectedRow]
        if (pkg is ExpandResultNode) loadMoreItems(service, pkg)
        return true
      }
    }.installOn(this)
  }

  private fun initCrossNavigation(service: PyPackagingToolWindowService, tablesView: PyPackagingTablesView) {
    getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ENTER_ACTION)
    actionMap.put(ENTER_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (selectedRow == -1) return
        val index = selectedRow
        val selectedItem = items[selectedRow]

        if (selectedItem is ExpandResultNode) {
          loadMoreItems(service, selectedItem)
        }
        setRowSelectionInterval(index, index)
      }
    })

    val nextRowAction = actionMap[NEXT_ROW_ACTION]
    actionMap.put(NEXT_ROW_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (selectedRow == -1) return

        if (selectedRow + 1 == items.size) {
          tablesView.selectNextFrom(this@PyPackagesTable)
        }
        else {
          nextRowAction.actionPerformed(e)
        }
      }
    })

    val prevRowAction = actionMap[PREVIOUS_ROW_ACTION]
    actionMap.put(PREVIOUS_ROW_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (selectedRow == -1) return

        if (selectedRow == 0) {
          tablesView.selectPreviousOf(this@PyPackagesTable)
        }
        else {
          prevRowAction.actionPerformed(e)
        }
      }
    })
  }

  @Suppress("UNCHECKED_CAST")
  private fun loadMoreItems(service: PyPackagingToolWindowService, node: ExpandResultNode) {
    val result = service.getMoreResultsForRepo(node.repository, items.size - 1)
    items = items.dropLast(1) + (result.packages as List<T>)
    if (result.moreItems > 0) {
      node.more = result.moreItems
      items = items + listOf(node) as List<T>
    }
    this@PyPackagesTable.revalidate()
    this@PyPackagesTable.repaint()
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
    return PyPaginationAwareRenderer()
  }

  override fun clearSelection() {
    lastSelectedRow = -1
    super.clearSelection()
  }

  internal fun addRows(rows: List<T>) = listModel.addRows(rows)
  internal fun removeRow(index: Int) = listModel.removeRow(index)
  internal fun insertRow(index: Int, pkg: T) = listModel.insertRow(index, pkg)

  @Suppress("UNCHECKED_CAST")
  private val listModel: ListTableModel<T>
    get() = model as ListTableModel<T>

  var items: List<T>
    get() = listModel.items
    set(value) { listModel.items = value.toMutableList() }

  companion object {
    private const val NEXT_ROW_ACTION = "selectNextRow"
    private const val PREVIOUS_ROW_ACTION = "selectPreviousRow"
    private const val ENTER_ACTION = "ENTER"
  }
}

internal class PyPackagesTableModel<T : DisplayablePackage> : ListTableModel<T>() {
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
    if (columnIndex == 0) {
      if (item is ExpandResultNode) {
        return message("python.toolwindow.packages.load.more", item.more)
      }
      return item.name
    }
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

fun headerPanel(label: JLabel, component: JComponent?) = object : JPanel() {
  init {
    background = UIUtil.getControlColor()
    layout = BorderLayout()
    border = BorderFactory.createCompoundBorder(SideBorder(UIUtil.getBoundsColor(), SideBorder.BOTTOM), EmptyBorder(0, 5, 0, 5))
    preferredSize = Dimension(preferredSize.width, 25)
    minimumSize = Dimension(minimumSize.width, 25)
    maximumSize = Dimension(maximumSize.width, 25)

    add(label, BorderLayout.WEST)
    if (component != null) {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          component.isVisible = !component.isVisible
          label.icon = if (component.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        }
      })
    }
  }
}

private class PyPaginationAwareRenderer : DefaultTableCellRenderer() {
  private val emptyBorder = BorderFactory.createEmptyBorder()
  private val expanderMarker = message("python.toolwindow.packages.load.more.start")
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    (component as DefaultTableCellRenderer).border = emptyBorder
    if (value is String && value.startsWith(expanderMarker)) {
      return component.apply {
        foreground = UIUtil.getContextHelpForeground()
      }
    }
    return component
  }
}


internal class PyPackagingTableGroup<T: DisplayablePackage>(val repository: PyPackageRepository, val table: PyPackagesTable<T>) {
  @NlsSafe val name: String = repository.name!!
  val repoUrl = repository.repositoryUrl ?: ""

  private var expanded = false
  private val label = JLabel(name).apply { icon = AllIcons.General.ArrowDown }
  private val header: JPanel = headerPanel(label, table)
  internal var itemsCount: Int? = null


  internal var items: List<T>
    get() = table.items
    set(value) {
      table.items = value
    }

  fun collapse() {
    expanded = false
    table.isVisible = false
    label.icon = if (table.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
  }

  fun expand() {
    expanded = true
    table.isVisible = true
    label.icon = if (table.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
  }

  fun updateHeaderText(newItemCount: Int?) {
    itemsCount = newItemCount
    label.text = if (itemsCount == null) name else message("python.toolwindow.packages.custom.repo.searched", name, itemsCount)
  }

  fun addTo(panel: JPanel) {
    panel.add(header)
    panel.add(table)
  }

  fun replace(row: Int, pkg: T) {
    table.removeRow(row)
    table.insertRow(row, pkg)
  }

  fun removeFrom(panel: JPanel) {
    panel.remove(header)
    panel.remove(table)
  }

  fun repaint() {
    table.invalidate()
    table.repaint()
  }
}