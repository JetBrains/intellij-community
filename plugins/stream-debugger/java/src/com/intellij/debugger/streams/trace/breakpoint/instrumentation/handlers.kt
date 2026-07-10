// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.Method
import com.sun.jdi.Value

// Simple stub to gradually implement runtime handlers
internal object NopHandler: SourceCallHandler, IntermediateCallHandler, TerminalCallHandler {
  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = value
  override fun beforeCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = value
  override fun transformArguments(evaluationContextImpl: EvaluationContextImpl, method: Method, arguments: List<Value?>): List<Value?> = arguments
  override fun result(evaluationContextImpl: EvaluationContextImpl): Value? = null
}

interface SourceCallHandler: AfterCallTransformer
interface IntermediateCallHandler : StreamOperationHandler
interface TerminalCallHandler : StreamOperationHandler

interface StreamOperationHandler : RuntimeTraceHandler, BeforeCallTransformer, CallTransformer, AfterCallTransformer

interface RuntimeTraceHandler {
  // What the handler has accumulated during tracing
  // For intermediate operations, it usually should return only infos array
  // For terminal operation it should return [infos, result]
  fun result(evaluationContextImpl: EvaluationContextImpl): Value?
}

interface CallTransformer {
  /**
   * Fires on entry to the method representing chain operator
   * This hook can be used for ex. to change predicate in filter operator
   *
   * @param[evaluationContextImpl] evaluation context for current breakpoint hit
   * @param[method] jdi method that represents operator
   * @param[arguments] list of arguments passed to operator
   * @return transformed arguments list
   */
  fun transformArguments(evaluationContextImpl: EvaluationContextImpl, method: Method, arguments: List<Value?>): List<Value?>
}

interface BeforeCallTransformer {
  /**
   * Fires on exit from the method representing _previous_ chain operator returns
   * (so, it fires before the next operation call)
   *
   * @param[evaluationContextImpl] evaluation context for current breakpoint hit
   * @param[value] stream chain instance as an operator result
   * @return transformed chain
   */
  fun beforeCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value?
}

interface AfterCallTransformer {
  /**
   * Fires before the method representing chain operator returns
   *
   * @param[evaluationContextImpl] evaluation context for current breakpoint hit
   * @param[value] value returned from terminal operation
   * @return transformed value
   */
  fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value?
}