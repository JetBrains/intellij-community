// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.NamedColorUtil
import com.jetbrains.python.packaging.toolwindow.PyPackagingTablesView
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.ExpandResultNode
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.table.TableCellRenderer

internal class PyPackagesTable<T : DisplayablePackage>(
  project: Project,
  model: ListTableModel<T>,
  tablesView: PyPackagingTablesView,
  val controller: PyPackagingToolWindowPanel,
) : JBTable(model) {
  private val scope = PyPackageCoroutine.getIoScope(project)

  private var lastSelectedRow = -1
  internal var hoveredColumn = -1

  @Suppress("UNCHECKED_CAST")
  private val listModel: ListTableModel<T>
    get() = model as ListTableModel<T>

  var items: List<T>
    get() = listModel.items
    set(value) {
      listModel.items = value.toMutableList()
    }

  init {
    val service = project.service<PyPackagingToolWindowService>()
    setShowGrid(false)
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    val column = columnModel.getColumn(1)
    column.minWidth = 130
    column.maxWidth = 130
    column.resizable = false
    border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM)
    rowHeight = 20

    initCrossNavigation(service, tablesView)

    val hoverListener = object : TableHoverListener() {
      override fun onHover(table: JTable, row: Int, column: Int) {
        hoveredColumn = column
        if (column == 1) {
          table.repaint(table.getCellRect(row, column, true))
          val currentPackage = items[row]
          if (currentPackage is InstallablePackage
              || (currentPackage is InstalledPackage && currentPackage.canBeUpdated)) {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            return
          }
        }
        cursor = Cursor.getDefaultCursor()
      }
    }
    hoverListener.addTo(this)

    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount != 1 || columnAtPoint(e.point) != 1) return // double click or click on package name column, nothing to be done
        val hoveredRow = TableHoverListener.getHoveredRow(this@PyPackagesTable)
        val selectedPackage = this@PyPackagesTable.items[hoveredRow]

        if (selectedPackage is InstallablePackage) {
          scope.launch(Dispatchers.IO) {
            val details = service.detailsForPackage(selectedPackage)
            withContext(Dispatchers.Main) {
              PyPackagesUiComponents.createAvailableVersionsPopup(selectedPackage, details, project).show(RelativePoint(e))
            }
          }
        }
        else if (selectedPackage is InstalledPackage && selectedPackage.canBeUpdated) {
          scope.launch(Dispatchers.IO) {
            val specification = selectedPackage.repository.createPackageSpecification(selectedPackage.name,
                                                                                      selectedPackage.nextVersion!!.presentableText)
            project.service<PyPackagingToolWindowService>().updatePackage(specification)
          }
        }
      }
    })

    selectionModel.addListSelectionListener {
      if (selectedRow != -1 && selectedRow != lastSelectedRow) {
        lastSelectedRow = selectedRow
        tablesView.requestSelection(this)
        val pkg = model.items[selectedRow]
        if (pkg !is ExpandResultNode) controller.packageSelected(pkg)
      }
    }

    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        val pkg = model.items[selectedRow]
        if (pkg is ExpandResultNode) loadMoreItems(service, pkg)
        return true
      }
    }.installOn(this)

    val packageActionGroup = ActionManager.getInstance().getAction("PyPackageToolwindowContext") as ActionGroup
    PopupHandler.installPopupMenu(this, packageActionGroup, "PackagePopup")
  }


  fun selectedItem(): T? = items.getOrNull(selectedRow)

  private fun initCrossNavigation(service: PyPackagingToolWindowService, tablesView: PyPackagingTablesView) {
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ENTER_ACTION)
    actionMap.put(ENTER_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (selectedRow == -1) return
        val index = selectedRow
        val selectedItem = selectedItem() ?: return

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

  internal fun removeRow(index: Int) = listModel.removeRow(index)
  internal fun insertRow(index: Int, pkg: T) = listModel.insertRow(index, pkg)


  companion object {
    private const val NEXT_ROW_ACTION = "selectNextRow"
    private const val PREVIOUS_ROW_ACTION = "selectPreviousRow"
    private const val ENTER_ACTION = "ENTER"
  }
}


