// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.wrapper.QualifierExpression
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.openapi.diagnostic.logger
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

private val LOG = logger<StreamInstrumentationManager>()

/**
 * Manages instrumentation of stream chain execution.
 *
 * Coordinates calls to handlers in the correct order:
 * 1. Source operation: `afterCall()`
 * 2. For each intermediate operation:
 *    - Entry: `transformArguments()`
 *    - Exit: `afterCall()` on current handler, then `beforeCall()` on next handler
 * 3. Terminal operation:
 *    - Entry: `transformArguments()`
 *    - Exit: `afterCall()`
 * 4. Collect results from all handlers
 *
 * Note: All methods of this class must be called only from the debugger manager thread.
 */
internal class StreamInstrumentationManager private constructor(
  private val objectStorage: ObjectStorage,
  private val sourceHandler: SourceCallHandler,
  private val intermediateHandlers: List<IntermediateCallHandler>,
  private val terminalHandler: TerminalCallHandler,
) {
  companion object {
    fun create(
      handlerFactory: BreakpointBasedHandlerFactory,
      objectStorage: ObjectStorage,
      chain: StreamChain,
      evaluationContext: EvaluationContextImpl,
    ): StreamInstrumentationManager {
      handlerFactory.beforeStreamTracing(evaluationContext)
      return StreamInstrumentationManager(
        objectStorage = objectStorage,
        sourceHandler = handlerFactory.getForSource(),
        intermediateHandlers = chain.intermediateCalls.mapIndexed { i, call ->
          handlerFactory.getForIntermediate(i, call)
        },
        terminalHandler = handlerFactory.getForTermination(chain.terminationCall),
      )
    }
  }

  // State for qualifier variable replacement and restoration
  private var originalQualifierValue: ObjectReference? = null
  private var qualifierVariableName: String? = null
  private var stackDepthWhenReplaced: Int = -1

  /**
   * Called when source operation (qualifier expression method) exits.
   * Applies afterCall() on source handler and beforeCall() on next handler.
   *
   * @return transformed stream value to be used as qualifier for first operation
   */
  fun onSourceOperationExit(
    evaluationContext: EvaluationContextImpl,
    value: Value?,
  ): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()

    val handler = sourceHandler
    return transformIfObjectReference(value) { streamValue ->
      // Source handler processes the stream
      val transformedValue = handler.afterCall(evaluationContext, streamValue)

      // Pass result to the next operation
      val nextHandler = getNextHandler(-1)
      nextHandler.beforeCall(evaluationContext, transformedValue)
    }
  }

  /**
   * Replace qualifier expression value in the current stack frame.
   * Used when qualifier is a simple variable reference (not a method call).
   *
   * Saves the original value and stack depth for later restoration.
   */
  fun replaceQualifierVariable(
    evaluationContext: EvaluationContextImpl,
    qualifierExpression: QualifierExpression,
  ): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()

    val frameProxy = evaluationContext.frameProxy!!

    val threadProxy = evaluationContext.suspendContext.thread!!
    val currentStackDepth = threadProxy.frameCount()

    val variable = frameProxy.visibleVariableByName(qualifierExpression.text)
    val originalValue = frameProxy.getValue(variable) as? ObjectReference ?: return null

    // Check if already replaced
    if (originalQualifierValue != null) {
      error("Qualifier expression value has already been replaced")
    }

    // Save state BEFORE transformation (in case transformation fails)
    originalQualifierValue = originalValue
    qualifierVariableName = qualifierExpression.text
    stackDepthWhenReplaced = currentStackDepth

    val transformedValue = sourceHandler.afterCall(evaluationContext, originalValue)

    // Pass to next handler
    val nextHandler = getNextHandler(-1)
    val finalValue = nextHandler.beforeCall(evaluationContext, transformedValue)

    frameProxy.setValue(variable, finalValue)

    return finalValue
  }

  /**
   * Restore the original qualifier variable value if it was replaced.
   * 
   * If no replacement was made, this method does nothing.
   */
  fun restoreQualifierVariableIfReplaced(evaluationContext: EvaluationContextImpl) {
    DebuggerManagerThreadImpl.assertIsManagerThread()

    // Nothing was replaced, nothing to restore
    if (originalQualifierValue == null) {
      return
    }

    val frameProxy = evaluationContext.frameProxy!!

    val threadProxy = evaluationContext.suspendContext.thread!!
    val currentStackDepth = threadProxy.frameCount()

    if (currentStackDepth != stackDepthWhenReplaced) {
      LOG.error(
        "Stack depth mismatch during qualifier restoration. " +
        "Expected: $stackDepthWhenReplaced, actual: $currentStackDepth. " +
        "This may indicate that the stream execution was unexpectedly interrupted."
      )
    }

    val variable = frameProxy.visibleVariableByName(qualifierVariableName!!)
    frameProxy.setValue(variable, originalQualifierValue)

    originalQualifierValue = null
    qualifierVariableName = null
    stackDepthWhenReplaced = -1
  }

  /**
   * Called on entry to an intermediate operation method.
   * Transforms arguments (e.g., replaces predicates/functions).
   *
   * @param callOrder 0-based index of the intermediate operation
   */
  fun onIntermediateOperationEntry(
    evaluationContext: EvaluationContextImpl,
    callOrder: Int,
    method: Method,
    arguments: List<Value?>,
  ): List<Value?> {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    require(callOrder in intermediateHandlers.indices) {
      "Invalid call order: $callOrder, available: ${intermediateHandlers.size}"
    }

    val handler = intermediateHandlers[callOrder]
    return handler.transformArguments(evaluationContext, method, arguments)
  }

  /**
   * Called when an intermediate operation method exits.
   * Applies `afterCall()` on current handler and `beforeCall()` on next handler.
   *
   * @param callOrder 0-based index of the intermediate operation
   * @return transformed stream value
   */
  fun onIntermediateOperationExit(
    evaluationContext: EvaluationContextImpl,
    callOrder: Int,
    value: Value?,
  ): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    require(callOrder in intermediateHandlers.indices) {
      "Invalid call order: $callOrder, available: ${intermediateHandlers.size}"
    }

    val handler = intermediateHandlers[callOrder]

    return transformIfObjectReference(value) { streamValue ->
      // Current handler processes the stream
      val transformedValue = handler.afterCall(evaluationContext, streamValue)

      // Pass result to the next operation
      val nextHandler = getNextHandler(callOrder)
      nextHandler.beforeCall(evaluationContext, transformedValue)
    }
  }

  fun onTerminalOperationEntry(
    evaluationContext: EvaluationContextImpl,
    method: Method,
    arguments: List<Value?>,
  ): List<Value?> {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return terminalHandler.transformArguments(evaluationContext, method, arguments)
  }

  fun onTerminalOperationExit(
    evaluationContext: EvaluationContextImpl,
    value: Value?,
  ): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return terminalHandler.afterCall(evaluationContext, value)
  }

  /**
   * Collects results from all handlers.
   * Should be called after stream chain evaluation completes.
   *
   * @return result value containing trace data from all operations
   */
  fun collectResults(evaluationContext: EvaluationContextImpl): Value {
    DebuggerManagerThreadImpl.assertIsManagerThread()

    return objectStorage.watch(evaluationContext) {
      val intermediateResults = intermediateHandlers.map { it.result(evaluationContext) }
      val terminalResult = terminalHandler.result(evaluationContext)

      // Format: [intermediateResults[], terminalResult, timing[]]
      // Note: timing is already included in terminal result, so we pass a placeholder
      array(
        array(*intermediateResults.toTypedArray()),
        terminalResult,
        array(0L.mirror)  // Placeholder for timing
      )
    }
  }

  /**
   * Get the next handler after the given call order.
   * -1 means source operation (next is first intermediate or terminal if no intermediates).
   */
  private fun getNextHandler(currentCallOrder: Int): BeforeCallTransformer {
    val nextIndex = currentCallOrder + 1
    return if (nextIndex < intermediateHandlers.size) {
      intermediateHandlers[nextIndex]
    }
    else {
      terminalHandler
    }
  }

  /**
   * Apply transformation only if value is an ObjectReference.
   * This is needed because stream operations return ObjectReference (stream instances).
   */
  private fun transformIfObjectReference(
    value: Value?,
    transformer: (ObjectReference) -> Value?
  ): Value? {
    return if (value is ObjectReference) {
      transformer(value)
    }
    else {
      value
    }
  }
}
