package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.runtime.snapshotFlow
import com.intellij.openapi.application.EDT
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.ProcessOutputUiContext
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.Component.TOP_ALIGNMENT
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import com.intellij.python.processOutput.frontend.ProcessTreeNode
import com.intellij.python.processOutput.frontend.ui.iterate

internal class ProcessTree(private val uiContext: ProcessOutputUiContext) {
  private val tree = Tree()
  private val rootNode = DefaultMutableTreeNode()
  private val treeModel = DefaultTreeModel(rootNode)
  private var previouslyExistingNodeIds = setOf<ProcessTreeNode.Id>()

  private val selectedProcess: LoggedProcess?
    get() =
      when (val modelTreeNode = tree.lastSelectedPathComponent as ProcessTreeNode?) {
        is ProcessTreeNode.Process -> modelTreeNode.loggedProcess
        is ProcessTreeNode.Context, null -> null
      }

  val component: JComponent
    field = ScrollPaneFactory.createScrollPane(tree, true)

  init {
    ClientProperty.put(tree, AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true) // animates spinners
    TreeHoverListener.DEFAULT.addTo(tree) // enables hover highlight

    tree.name = Naming.TREE_NAME
    tree.cellRenderer = ProcessTreeCellRenderer(uiContext)
    tree.isRootVisible = false
    tree.model = treeModel
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    tree.alignmentX = LEFT_ALIGNMENT
    tree.alignmentY = TOP_ALIGNMENT
    tree.emptyText.text = message("process.output.tree.blankMessage")

    component.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.processTreeUiState.treeRoot.collect { newNodes ->
        synchronizeTree(newNodes, component)
      }
    }

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      snapshotFlow { uiContext.controller.processTreeUiState.filters.active.toSet() }.collect {
        tree.repaint()
      }
    }

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.selectedProcess.collect { newlySelectedProcess ->
        if (newlySelectedProcess?.data?.id == selectedProcess?.data?.id) {
          return@collect
        }

        if (newlySelectedProcess == null) {
          tree.selectionPath = null
          return@collect
        }

        val nodes = mutableMapOf<Int, DefaultMutableTreeNode>()

        rootNode.children().iterate {
          when (it) {
            is ProcessTreeNode.Process -> {
              nodes[it.loggedProcess.data.id] = it
            }
            is ProcessTreeNode.Context -> Unit
          }
        }

        nodes[newlySelectedProcess.data.id]?.also { nodeToSelect ->
          val path = TreePath(nodeToSelect.path)

          tree.selectionPath = path
          tree.scrollPathToVisible(path)
        }
      }
    }

    tree.addTreeSelectionListener {
      if (selectedProcess?.data?.id == uiContext.controller.selectedProcess.value?.data?.id) {
        return@addTreeSelectionListener
      }

      uiContext.controller.selectProcess(selectedProcess)
    }

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.processStatusUpdates.collect {
        tree.repaint()
      }
    }
  }

  fun expandAll() {
    val toExpand = mutableSetOf<DefaultMutableTreeNode>()

    rootNode.children().iterate { child ->
      when (child) {
        is ProcessTreeNode.Context -> {
          toExpand += child
        }
        is ProcessTreeNode.Process -> {}
      }
    }

    tree.expandPaths(toExpand.map { TreePath(it.path) })
  }

  fun collapseAll() {
    val expandedPaths = tree.expandedPaths - TreePath(rootNode.path)
    tree.collapsePaths(expandedPaths)
  }

  private fun synchronizeTree(newNodes: List<ProcessTreeNode>, scrollPane: JScrollPane) {
    val selectedNodeId = (tree.selectionPath?.lastPathComponent as ProcessTreeNode?)?.id
    val expandedNodeIds = tree.expandedPaths.mapNotNull { (it.lastPathComponent as? ProcessTreeNode)?.id }
    val scrollProgress = scrollPane.viewport.viewPosition.y

    val action = {
      rootNode.removeAllChildren()

      for (child in newNodes) {
        rootNode.add(child)
      }

      treeModel.nodeStructureChanged(rootNode)

      var nodeToSelect: ProcessTreeNode? = null
      val nodesToExpand = mutableListOf<ProcessTreeNode>()
      val newNodeIds = mutableSetOf<ProcessTreeNode.Id>()

      rootNode.children().iterate {
        newNodeIds += it.id

        if (it.id !in previouslyExistingNodeIds || it.id in expandedNodeIds) {
          nodesToExpand += it
        }

        if (it.id == selectedNodeId) {
          nodeToSelect = it
        }
      }

      previouslyExistingNodeIds = newNodeIds
      tree.expandPaths(nodesToExpand.map { TreePath(it.path) })

      if (nodeToSelect != null) {
        tree.selectionPath = TreePath(nodeToSelect.path)
      }
      else {
        uiContext.controller.selectProcess(null)
      }
    }

    if (scrollProgress == 0) {
      action()
    }
    else {
      withPreservedScrollAnchor(action)
    }
  }

  private fun withPreservedScrollAnchor(block: () -> Unit) {
    val topY = component.viewport.viewPosition.y

    val anchorPath = tree.getClosestPathForLocation(0, topY)
    val anchorPathId = (anchorPath.lastPathComponent as? ProcessTreeNode)?.id
    val anchorRowY = anchorPath?.let { tree.getPathBounds(it)?.y } ?: topY
    val anchorInnerOffset = topY - anchorRowY

    block()
    component.validate()

    var newAnchor: TreePath? = null
    rootNode.children().iterate {
      if (it.id == anchorPathId) {
        newAnchor = TreePath(it.path)
      }
    }
    val newAnchorY = newAnchor?.let { tree.getPathBounds(it)?.y } ?: return
    component.viewport.viewPosition = Point(component.viewport.viewPosition.x, newAnchorY + anchorInnerOffset)
  }

  private object Naming {
    const val TREE_NAME = "Python.ProcessOutput.Tree"
  }
}
