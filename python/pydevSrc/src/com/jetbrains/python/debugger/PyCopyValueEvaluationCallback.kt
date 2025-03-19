// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.ui.tree.actions.XFetchValueActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.HeadlessValueEvaluationCallback
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

class PyCopyValueEvaluationCallback(node: XValueNodeImpl,
                                    private val valueCollector: XFetchValueActionBase.ValueCollector,
                                    private val currentPolicy: QuotingPolicy) : HeadlessValueEvaluationCallback(node) {
  private val valueIndex = valueCollector.acquire()

  override fun evaluationComplete(value: String, project: Project) {
    valueCollector.evaluationComplete(valueIndex, getQuotingString(currentPolicy, value))
  }
}