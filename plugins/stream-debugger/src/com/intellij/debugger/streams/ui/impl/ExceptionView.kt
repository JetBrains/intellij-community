// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import javax.swing.JTree

/**
 * @author Vitaliy.Bibaev
 */
class ExceptionView(context: EvaluationContextImpl, ex: TraceElement)
  : CollectionView(JBLabel("Cause"), SingleElementTree(ex.value!!, listOf(ex), context)) {
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