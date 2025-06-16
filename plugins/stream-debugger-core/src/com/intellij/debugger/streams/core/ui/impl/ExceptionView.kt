// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.ui.impl

import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.GenericEvaluationContext
import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import javax.swing.JTree

/**
 * @author Vitaliy.Bibaev
 */
class ExceptionView(launcher: DebuggerCommandLauncher, context: GenericEvaluationContext, ex: TraceElement, builder: CollectionTreeBuilder)
  : CollectionView(JBLabel(StreamDebuggerBundle.message("exception.label")),
                   TerminationTree(ex.value!!, listOf(ex), launcher, context, builder, "ExceptionView")) {
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