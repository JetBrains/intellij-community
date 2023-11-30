package com.jetbrains.performancePlugin.remotedriver.dataextractor

import org.assertj.swing.cell.JTreeCellReader
import java.awt.Dimension
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

internal class JTreeTextCellReader : JTreeCellReader {
  override fun valueAt(tree: JTree, modelValue: Any?): String? {
    if (modelValue == null) return null
    val isLeaf = modelValue is DefaultMutableTreeNode && modelValue.isLeaf
    return computeOnEdt {
      val cellRendererComponent =
        tree.cellRenderer.getTreeCellRendererComponent(tree, modelValue, false, false, isLeaf, 0, false)
      cellRendererComponent.size = Dimension(tree.width, 100)
      TextParser.parseCellRenderer(cellRendererComponent).joinToString(" ") { it.trim() }
    }
  }
}