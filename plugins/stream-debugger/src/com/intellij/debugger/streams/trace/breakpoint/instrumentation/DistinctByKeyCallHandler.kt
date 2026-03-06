// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.java.debugger.streams.rt.ArgumentRecordingWrapper
import com.intellij.java.debugger.streams.rt.StreamDebuggerUtils
import com.sun.jdi.ArrayReference
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Handler for StreamEx `distinct(keyExtractor)` — wraps the key extractor in a keyExtractor
 * that records each computed key in order, then uses the captured keys to build the
 * equivalence-class mapping. This avoids re-applying the extractor after execution,
 * which would produce wrong results for stateful extractors.
 */
internal class DistinctByKeyCallHandler(
  objectStorage: ObjectStorage,
  private val typeBefore: GenericType?,
  private val typeAfter: GenericType?,
  time: ObjectReference,
) : PeekCallHandler(objectStorage, typeBefore, typeAfter, time) {

  // ArgumentRecordingWrapper created during transformArguments; records each computed key in order.
  private var keyExtractor: ObjectReference? = null

  override fun transformArguments(
    evaluationContextImpl: EvaluationContextImpl,
    method: Method,
    arguments: List<Value?>,
  ): List<Value?> {
    val original = arguments.firstOrNull() as? ObjectReference ?: return arguments
    val wrapper = objectStorage.watch(evaluationContextImpl) {
      // Use instance() so ClassLoadingUtils explicitly loads ArgumentRecordingWrapper into the target VM
      // (it is not loaded automatically when StreamDebuggerUtils is loaded).
      instance(ArgumentRecordingWrapper::class.java, "(Ljava/util/function/Function;)V", listOf(original))
    }
    keyExtractor = wrapper
    return listOf(wrapper) + arguments.drop(1)
  }

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val beforeFormatted = beforeValuesMap?.let { formatMap(it, typeBefore!!) } ?: emptyResult()
      val afterFormatted = afterValuesMap?.let { formatMap(it, typeAfter!!) } ?: emptyResult()
      val peekTrace = array(beforeFormatted, afterFormatted)
      val mapping = computeKeyBasedMapping()
      array(peekTrace, mapping)
    }
  }

  private fun ValueContext.computeKeyBasedMapping(): ArrayReference {
    val before = beforeValuesMap
    val after = afterValuesMap
    val keyExtractorRef = keyExtractor
    if (before == null || after == null || keyExtractorRef == null) {
      return array(array("int", 0), array("int", 0))
    }
    val utilsClass = clazz(StreamDebuggerUtils::class.java)
    val method = utilsClass.method(
      "computeDistinctByRecordedKeyMapping",
      "(Ljava/util/Map;Ljava/util/Map;Ljava/util/function/Function;)[Ljava/lang/Object;"
    )
    return method.invoke(utilsClass, listOf(before, after, keyExtractorRef)) as ArrayReference
  }
}

/**
 * Handler for StreamEx `distinctKeys()`/`distinctValues()` — groups Map.Entry elements
 * by a fixed entry projection (key or value) computed in the target VM.
 */
internal class DistinctByMapEntryCallHandler(
  objectStorage: ObjectStorage,
  private val typeBefore: GenericType?,
  private val typeAfter: GenericType?,
  time: ObjectReference,
  private val utilsMethodName: String,
) : PeekCallHandler(objectStorage, typeBefore, typeAfter, time) {

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val beforeFormatted = beforeValuesMap?.let { formatMap(it, typeBefore!!) } ?: emptyResult()
      val afterFormatted = afterValuesMap?.let { formatMap(it, typeAfter!!) } ?: emptyResult()
      val peekTrace = array(beforeFormatted, afterFormatted)
      val mapping = computeMapping()
      array(peekTrace, mapping)
    }
  }

  private fun ValueContext.computeMapping(): ArrayReference {
    val before = beforeValuesMap
    val after = afterValuesMap
    if (before == null || after == null) return array(array("int", 0), array("int", 0))
    val utilsClass = clazz(StreamDebuggerUtils::class.java)
    val method = utilsClass.method(utilsMethodName, "(Ljava/util/Map;Ljava/util/Map;)[Ljava/lang/Object;")
    return method.invoke(utilsClass, listOf(before, after)) as ArrayReference
  }
}
