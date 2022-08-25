// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.actions

import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.actions.XCopyValueAction
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.debugger.PyCopyValueEvaluationCallback

class PyXCopyValueAction : XCopyValueAction() {

  override fun addToCollector(paths: MutableList<XValueNodeImpl>, valueNode: XValueNodeImpl, valueCollector: ValueCollector) {
    // Call parent method in not Python files in IDEs with Python plugin
    if (valueCollector.tree?.editorsProvider?.fileType !is PythonFileType) {
      super.addToCollector(paths, valueNode, valueCollector)
      return
    }

    if (paths.size > 1) {
      valueCollector.add(valueNode.text.toString(), valueNode.path.pathCount)
    }
    else {
      val fullValueEvaluator = valueNode.fullValueEvaluator
      if (fullValueEvaluator != null) {
        PyCopyValueEvaluationCallback(valueNode, valueCollector).startFetchingValue(fullValueEvaluator)
      }
      else {
        valueCollector.add((DebuggerUIUtil.getNodeRawValue(valueNode) ?: ""))
      }
    }
  }
}