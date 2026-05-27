// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.trace

import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import org.jetbrains.annotations.NonNls

/**
 * @author Vitaliy.Bibaev
 */
interface TraceExpressionBuilder {
  fun createTraceExpression(chain: StreamChain): @NonNls String

  fun createXExpression(chain: StreamChain, expressionText: String): XExpression {
    return XExpressionImpl.fromText(expressionText, EvaluationMode.CODE_FRAGMENT)
  }
}
