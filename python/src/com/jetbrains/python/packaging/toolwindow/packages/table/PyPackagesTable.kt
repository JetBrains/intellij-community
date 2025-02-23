// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.table

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.NamedColorUtil
import com.jetbrains.python.packaging.toolwindow.PyPackagingTablesView
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.ExpandResultNode
import com.jetbrains.python.packaging.toolwindow.packages.PyPaginationAwareRenderer
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesTableModel
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel

internal class PyPackagesTable(
  val project: Project,
  tablesView: PyPackagingTablesView,
  val controller: PyPackagingToolWindowPanel,
) : JBTable(PyPackagesTableModel<DisplayablePackage>()) {
  internal var hoveredColumn = -1

  @Suppress("UNCHECKED_CAST")
  val model: PyPackagesTableModel<DisplayablePackage> = getModel() as PyPackagesTableModel<DisplayablePackage>

  var items: List<DisplayablePackage>
    get() = model.items
    set(value) {
      model.items = value.toMutableList()
    }

  init {
    val service = project.service<PyPackagingToolWindowService>()
    setShowGrid(false)
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)

    val column = columnModel.getColumn(1)
    column.minWidth = 130
    column.maxWidth = 130
    column.resizable = false
    border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM)
    rowHeight = 20

    initCrossNavigation(service, tablesView)

    val hoverListener = PyPackagesHoverListener(this)
    hoverListener.addTo(this)

    addMouseListener(PyPackageTableMouseAdapter(this))

    selectionModel.addListSelectionListener {
      val pkg = selectedItem()
      if (pkg != null && pkg !is ExpandResultNode) {
        tablesView.removeSelectionNotFormTable(this)
        controller.packageSelected(pkg)
      }
      else {
        controller.setEmpty()
      }
    }

    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        val pkg = selectedItem() ?: return true
        if (pkg is ExpandResultNode)
          loadMoreItems(service, pkg)
        return true
      }
    }.installOn(this)

    val packageActionGroup = ActionManager.getInstance().getAction("PyPackageToolwindowContext") as ActionGroup
    PopupHandler.installPopupMenu(this, packageActionGroup, "PackagePopup")
  }

  override fun getCellRenderer(row: Int, column: Int) = PyPaginationAwareRenderer()

  fun selectedItem(): DisplayablePackage? = items.getOrNull(selectedRow)

  fun selectedItems(): Sequence<DisplayablePackage> {
    return selectedRows.asSequence().mapNotNull { items.getOrNull(it) }
  }

  fun selectPackage(pkg: DisplayablePackage) {
    val index = items.indexOf(pkg)
    if (index != -1) {
      setRowSelectionInterval(index, index)
    }
  }

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

  private fun loadMoreItems(service: PyPackagingToolWindowService, node: ExpandResultNode) {
    val result = service.getMoreResultsForRepo(node.repository, items.size - 1)
    items = items.dropLast(1) + result.packages
    if (result.moreItems > 0) {
      node.more = result.moreItems
      items = items + listOf(node)
    }
    this@PyPackagesTable.revalidate()
    this@PyPackagesTable.repaint()
  }

  companion object {
    private const val NEXT_ROW_ACTION = "selectNextRow"
    private const val PREVIOUS_ROW_ACTION = "selectPreviousRow"
    private const val ENTER_ACTION = "ENTER"
  }
}