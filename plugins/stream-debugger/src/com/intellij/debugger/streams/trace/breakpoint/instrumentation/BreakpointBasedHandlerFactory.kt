// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.sun.jdi.Method
import com.sun.jdi.Value

// TODO: include time in the implementation
class BreakpointBasedHandlerFactory {
  /**
   * Prepares stream for further transformation.
   * The stream can be parallel, so to be able to restore the order
   * of transformation, we need to make it sequential then add counter.
   */
  fun getForSource(): SourceCallHandler = NopHandler
  fun getForIntermediate(number: Int, call: IntermediateStreamCall): IntermediateCallHandler = NopHandler
  fun getForTermination(call: TerminatorStreamCall): TerminalCallHandler = NopHandler
}

object NopHandler: SourceCallHandler, IntermediateCallHandler, TerminalCallHandler {
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
  // Что накопил в себе handler
  fun result(evaluationContextImpl: EvaluationContextImpl): Value?
}

interface CallTransformer {
  /**
   * Fires immediately after method representing chain operator was called
   * This hook can be used for ex. to change predicate in filter operator
   *
   * @param[evaluationContextImpl] evaluation context for current breakpoint hit
   * @param[method] jdi method which represents operator
   * @param[arguments] list of arguments passed to operator
   * @return transformed arguments list
   */
  fun transformArguments(evaluationContextImpl: EvaluationContextImpl, method: Method, arguments: List<Value?>): List<Value?>
}

interface BeforeCallTransformer {
  /**
   * Fires before method representing previous chain operator returns
   * (so, it fires before next operation call)
   *
   * @param[evaluationContextImpl] evaluation context for current breakpoint hit
   * @param[value] stream chain instance as operator result
   * @return transformed chain
   */
  fun beforeCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value?
}

interface AfterCallTransformer {
  /**
   * Fires before method representing chain operator returns
   *
   * @param[evaluationContextImpl] evaluation context for current breakpoint hit
   * @param[value] value returned from terminal operation
   * @return transformed value
   */
  fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value?
}