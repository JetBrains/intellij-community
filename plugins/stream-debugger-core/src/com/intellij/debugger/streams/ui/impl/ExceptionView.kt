// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.StreamDebuggerBundle
import com.intellij.debugger.streams.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.trace.EvaluationContextWrapper
import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import javax.swing.JTree

/**
 * @author Vitaliy.Bibaev
 */
class ExceptionView(context: EvaluationContextWrapper, ex: TraceElement, builder: CollectionTreeBuilder)
  : CollectionView(JBLabel(StreamDebuggerBundle.message("exception.label")), SingleElementTree(listOf(ex), context, builder, "ExceptionView")) {
  init {
    instancesTree.cellRenderer = object : TraceTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
        if (row == 0) {
          // TODO: add this icon to the plugin
          icon = AllIcons.Nodes.ErrorIntroduction
        }
      }
    }
  }
}