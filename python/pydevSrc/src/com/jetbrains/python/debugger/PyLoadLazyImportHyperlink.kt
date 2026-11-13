// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.MessageType
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeEx
import com.jetbrains.python.PydevBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent

/**
 * Adds the "Load" link to a PEP 810 lazy import that the debugger shows unloaded.
 */
@ApiStatus.Internal
fun addLoadLazyImportLink(debugValue: PyDebugValue, valueNode: XValueNodeEx) {
  if (debugValue.type != NodeTypes.LAZY_IMPORT_NODE_TYPE) return
  if (!debugValue.frameAccessor.canLoadLazyImports()) return
  valueNode.addAdditionalHyperlink(PyLoadLazyImportHyperlink(debugValue))
}

/**
 * Implements [PyFrameAccessor.loadLazyImport] for an accessor that the [session] shows. The [scope] runs the load.
 * The load evaluates the name in the frame, and the debugger backend replaces the lazy import with the loaded value.
 */
@ApiStatus.Internal
fun launchLazyImportLoad(scope: CoroutineScope, frameAccessor: PyFrameAccessor, value: PyDebugValue, session: XDebugSession) {
  scope.launch(Dispatchers.IO) {
    try {
      val result = frameAccessor.evaluate(value.evaluationExpression, false, true)
      if (result.isErrorOnEval) {
        session.reportMessage(result.value.orEmpty(), MessageType.ERROR)
      }
    }
    catch (e: PyDebuggerException) {
      LOG.warn(e)
      session.reportMessage(e.tracebackError, MessageType.ERROR)
    }
    session.rebuildViews()
  }
}

private class PyLoadLazyImportHyperlink(
  private val debugValue: PyDebugValue,
) : XDebuggerTreeNodeHyperlink(PydevBundle.message("pydev.load.lazy.import")) {
  override fun alwaysOnScreen(): Boolean = true

  override fun onClick(event: MouseEvent) {
    debugValue.frameAccessor.loadLazyImport(debugValue)
  }
}

private val LOG = logger<PyLoadLazyImportHyperlink>()
