// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.java.debugger.streams.rt.StreamDebuggerUtils
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

internal class DistinctCallHandler(
  objectStorage: ObjectStorage,
  private val typeBefore: GenericType?,
  private val typeAfter: GenericType?,
  time: ObjectReference,
) : PeekCallHandler(objectStorage, typeBefore, typeAfter, time) {

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val beforeFormatted = beforeValuesMap?.let { formatMap(it, typeBefore!!) } ?: emptyResult()
      val afterFormatted = afterValuesMap?.let { formatMap(it, typeAfter!!) } ?: emptyResult()
      val peekTrace = array(beforeFormatted, afterFormatted)
      val mapping = computeDistinctMapping()
      array(peekTrace, mapping)
    }
  }

  private fun ValueContext.computeDistinctMapping(): ArrayReference {
    val before = beforeValuesMap
    val after = afterValuesMap
    if (before == null || after == null) {
      return array(array("int", 0), array("int", 0))
    }
    val utilsClass = clazz(StreamDebuggerUtils::class.java)
    val method = utilsClass.method("computeDistinctMapping", "(Ljava/util/Map;Ljava/util/Map;)[Ljava/lang/Object;")
    return method.invoke(utilsClass, listOf(before, after)) as ArrayReference
  }
}
