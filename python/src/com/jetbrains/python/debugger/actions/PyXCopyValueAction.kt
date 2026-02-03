// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.actions.XCopyValueAction
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.debugger.PyCopyValueEvaluationCallback
import com.jetbrains.python.debugger.getQuotingString
import com.jetbrains.python.debugger.settings.PyDebuggerSettings

class PyXCopyValueAction : XCopyValueAction() {

  @Suppress("RedundantInnerClassModifier")
  private inner class MyCollector(val session: XDebugSessionProxy) : ValueCollector(session.project)

  override fun createCollector(e: AnActionEvent): ValueCollector {
    val session = DebuggerUIUtil.getSessionProxy(e)
    if (session == null || isNotPython(session)) {
      return super.createCollector(e)
    }
    return MyCollector(session)
  }

  override fun addToCollector(paths: MutableList<XValueNodeImpl>, valueNode: XValueNodeImpl, valueCollector: ValueCollector) {
    // Call parent method in not Python files in IDEs with Python plugin
    val session = (valueCollector as? MyCollector)?.session
    if (session == null || isNotPython(session)) {
      super.addToCollector(paths, valueNode, valueCollector)
      return
    }

    if (paths.size > 1) {
      valueCollector.add(valueNode.text.toString(), valueNode.path.pathCount)
    }
    else {
      val fullValueEvaluator = valueNode.fullValueEvaluator
      val quotingPolicy = PyDebuggerSettings.getInstance().quotingPolicy
      if (fullValueEvaluator != null) {
        PyCopyValueEvaluationCallback(valueNode, valueCollector, quotingPolicy).startFetchingValue(fullValueEvaluator)
      }
      else {
        valueCollector.add(getQuotingString(quotingPolicy, DebuggerUIUtil.getNodeRawValue(valueNode) ?: ""))
      }
    }
  }

  private fun isNotPython(session: XDebugSessionProxy): Boolean = session.editorsProvider.fileType !is PythonFileType
}