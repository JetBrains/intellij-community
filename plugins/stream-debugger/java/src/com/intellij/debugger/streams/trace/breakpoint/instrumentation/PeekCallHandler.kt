// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.java.rt.collectors.UniversalCollector
import com.sun.jdi.*

/**
 * Base handler that wraps streams via: `stream.peek(collector)` to collect values
 * Collector saves values in Map<Integer, Value> indexed by execution order
 * @param typeBefore type before this operation
 * @param typeAfter type after this operation
 * @param time shared AtomicInteger for counting elements flowing through the stream
 */
internal open class PeekCallHandler(
  protected val objectStorage: ObjectStorage,
  private val typeBefore: GenericType?,
  private val typeAfter: GenericType?,
  protected val time: ObjectReference,
) : IntermediateCallHandler {

  protected var beforeValuesMap: ObjectReference? = null
  protected var afterValuesMap: ObjectReference? = null

  override fun beforeCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    if (typeBefore == null || value !is ObjectReference) return value
    return objectStorage.watch(evaluationContextImpl) { wrapWithCollector(value, typeBefore, isBeforeCall = true) }
  }

  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    if (typeAfter == null || value !is ObjectReference) return value
    return objectStorage.watch(evaluationContextImpl) { wrapWithCollector(value, typeAfter, isBeforeCall = false) }
  }

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val beforeFormatted = beforeValuesMap?.let { formatMap(it, typeBefore!!) } ?: emptyResult()
      val afterFormatted = afterValuesMap?.let { formatMap(it, typeAfter!!) } ?: emptyResult()
      array(beforeFormatted, afterFormatted)
    }
  }

  override fun transformArguments(evaluationContextImpl: EvaluationContextImpl, method: Method, arguments: List<Value?>): List<Value?> = arguments

  private fun ValueContext.wrapWithCollector(streamObject: ObjectReference, type: GenericType, isBeforeCall: Boolean): ObjectReference {
    val valuesMap = instance(LinkedHashMap::class.java)
    if (isBeforeCall) {
      beforeValuesMap = valuesMap
    } else {
      afterValuesMap = valuesMap
    }

    val streamTypeInfo = StreamTypeInfo.forType(type.genericTypeName)
    val shouldTick = !isBeforeCall
    val collector = instance(
      UniversalCollector::class.java,
      UNIVERSAL_COLLECTOR_CONSTRUCTOR_SIGNATURE,
      listOf(valuesMap, time, shouldTick.mirror)
    )

    val streamType = streamObject.referenceType() as ClassType
    val peekMethod = streamType.method("peek", streamTypeInfo.peekSignature)

    return peekMethod.invoke(
      streamObject,
      listOf(collector)
    ) as ObjectReference
  }

}
