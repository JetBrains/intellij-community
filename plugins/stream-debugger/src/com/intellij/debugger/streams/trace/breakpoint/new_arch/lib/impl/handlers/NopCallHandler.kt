// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.impl.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeIntermediateCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeSourceCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeTerminalCallHandler
import com.sun.jdi.Value

class NopCallHandler : RuntimeSourceCallHandler, RuntimeIntermediateCallHandler, RuntimeTerminalCallHandler {
  override fun result(evaluationContextImpl: EvaluationContextImpl): Value? = null

  override fun beforeCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = value

  override fun transformArguments(evaluationContextImpl: EvaluationContextImpl, arguments: List<Value?>): List<Value?> = arguments

  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = value
}