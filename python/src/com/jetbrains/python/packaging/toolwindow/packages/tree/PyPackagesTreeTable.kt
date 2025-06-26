// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBTreeTable
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.ExpandResultNode
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.PackageNameCellRenderer
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.PackageVersionCellRenderer
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION

@ApiStatus.Internal
class PyPackagesTreeTable(
  val project: Project,
  private val controller: PyPackagingToolWindowPanel,
  private var treeListener: PyPackagesTreeListener? = null,
) : JBTreeTable(PyPackagesTreeTableModel()), PackageTreeTableOperations {

  companion object {
    private const val COLUMN_PROPORTION = 0.3f
    private const val POPUP_MENU_PLACE = "PackagePopup"
    private const val PACKAGE_ACTION_GROUP_ID = "PyPackageToolwindowContext"
    private const val INVALID_POSITION = -1
    internal val TREE_TABLE_KEY: Key<PyPackagesTreeTable> = Key.create("PyPackageToolwindow.TreeTable")
  }

  private val treeTableModel: PyPackagesTreeTableModel
    get() = model as PyPackagesTreeTableModel
  private val packagingService = project.service<PyPackagingToolWindowService>()

  var hoveredColumn: Int = INVALID_POSITION

  var items: List<DisplayablePackage> = emptyList()
    set(value) {
      field = value
      treeTableModel.items = value
    }

  init {
    table.putUserData(TREE_TABLE_KEY, this)
    initializeUI()
  }

  private fun initializeUI() {
    initializeTreeTableProperties()
    initializeTreeProperties()
    initializeCellRenderers()
    setupTreeInteractions()
    setupContextMenu()
  }

  private fun initializeTreeTableProperties() {
    splitter.setResizeEnabled(false)
    val secondComponent = splitter.secondComponent as JScrollPane
    secondComponent.apply {
      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
    }
    setColumnProportion(COLUMN_PROPORTION)
  }

  private fun initializeTreeProperties() {
    tree.apply {
      isRootVisible = false
      showsRootHandles = true
      selectionModel.selectionMode = SINGLE_TREE_SELECTION
    }
  }

  private fun initializeCellRenderers() {
    setDefaultRenderer(TreeTableModel::class.java, PackageNameCellRenderer())
    setDefaultRenderer(DisplayablePackage::class.java, PackageVersionCellRenderer())
  }

  private fun setupTreeInteractions() {
    setupTreeEventListeners()
    setupMouseAndHoverHandlers()
  }

  private fun setupTreeEventListeners() {
    tree.addTreeSelectionListener(createPackageSelectionListener())
    tree.addTreeExpansionListener(createTreeExpansionListener())
    installPackageDoubleClickHandler()
  }

  private fun createPackageSelectionListener() = TreeSelectionListener { event ->
    val path = event.path ?: return@TreeSelectionListener
    val node = path.lastPathComponent
    when (val pkg = treeTableModel.getValueAt(node, 0)) {
      is DisplayablePackage -> controller.packageSelected(pkg)
      else -> controller.setEmpty()
    }
  }

  private fun createTreeExpansionListener() = object : TreeExpansionListener {
    override fun treeExpanded(event: TreeExpansionEvent) {
      treeListener?.onTreeStructureChanged()
    }
    override fun treeCollapsed(event: TreeExpansionEvent) {
      treeListener?.onTreeStructureChanged()
    }
  }

  private fun setupMouseAndHoverHandlers() {
    val mouseHandler = PyPackageTableMouseAdapter(this)
    tree.addMouseListener(mouseHandler)
    table.addMouseListener(mouseHandler)

    setupHoverTracking()
  }

  private fun setupHoverTracking() {
    TreeHoverListener.DEFAULT.addTo(tree)
    TableHoverListener.DEFAULT.addTo(table)
    object : TableHoverListener() {
      override fun onHover(table: JTable, row: Int, column: Int) {
        hoveredColumn = column
      }
    }.addTo(table)
  }

  private fun installPackageDoubleClickHandler() {
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        val pkg = selectedItem() as? ExpandResultNode ?: return true
        loadMoreItems(pkg)
        return true
      }
    }.installOn(tree)
  }

  private fun setupContextMenu() {
    val packageActionGroup = ActionManager.getInstance()
      .getAction(PACKAGE_ACTION_GROUP_ID) as ActionGroup
    PopupHandler.installPopupMenu(tree, packageActionGroup, POPUP_MENU_PLACE)
    PopupHandler.installPopupMenu(table, packageActionGroup, POPUP_MENU_PLACE)
  }

  private fun loadMoreItems(node: ExpandResultNode) {
    val result = packagingService.getMoreResultsForRepo(node.repository, items.size - 1) ?: return
    items = items.dropLast(1) + result.packages
    if (result.moreItems > 0) {
      node.more = result.moreItems
      items = items + listOf(node)
    }
  }

  override fun setTreeListener(listener: PyPackagesTreeListener) {
    treeListener = listener
  }

  override fun selectPackage(pkg: DisplayablePackage) {
    val index = items.indexOf(pkg)
    if (index != -1) {
      tree.setSelectionRow(index)
    }
  }

  override fun selectedItems(): Sequence<DisplayablePackage> =
    tree.selectionRows?.asSequence()?.mapNotNull { row ->
      val node = tree.getPathForRow(row)?.lastPathComponent ?: return@mapNotNull null
      treeTableModel.getValueAt(node, 0) as? DisplayablePackage
    } ?: emptySequence()
}

private interface PackageTreeTableOperations {
  fun setTreeListener(listener: PyPackagesTreeListener)
  fun selectPackage(pkg: DisplayablePackage)
  fun selectedItems(): Sequence<DisplayablePackage>
  fun selectedItem(): DisplayablePackage? = selectedItems().firstOrNull()
}

@ApiStatus.Internal
interface PyPackagesTreeListener {
  fun onTreeStructureChanged()
}