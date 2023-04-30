// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.impl.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.psi.CommonClassNames
import com.sun.jdi.Value
import com.sun.jdi.VoidValue

class PeekTerminalCallHandler(valueManager: ValueManager, typeBefore: GenericType?, typeAfter: GenericType?) : PeekCallHandler(valueManager,
                                                                                                                               typeBefore,
                                                                                                                               typeAfter) {
  private var streamResult: Value? = null

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value = valueManager.watch(evaluationContextImpl) {
    val wrappedStreamResult = if (streamResult is VoidValue) {
      array(CommonClassNames.JAVA_LANG_OBJECT, 1)
    }
    else {
      array(streamResult)
    }
    array(
      super.result(evaluationContextImpl),
      wrappedStreamResult
    )
  }

  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? {
    streamResult = value
    return value
  }
}