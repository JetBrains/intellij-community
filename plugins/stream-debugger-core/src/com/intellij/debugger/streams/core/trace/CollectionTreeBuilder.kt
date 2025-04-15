// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace

import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueContainer

interface CollectionTreeBuilder {
  fun isSupported(container: XValueContainer): Boolean

  fun createXNamedValue(value: Value?, evaluationContext: GenericEvaluationContext): XNamedValue

  /**
   * Is called under `com.intellij.debugger.streams.trace.EvaluationContextWrapper.launchDebuggerCommand`
   */
  fun getKey(container: XValueContainer, nullMarker: Any): Any

  fun getKey(traceElement: TraceElement, nullMarker: Any): Any

  fun getEditorsProvider() : XDebuggerEditorsProvider
}