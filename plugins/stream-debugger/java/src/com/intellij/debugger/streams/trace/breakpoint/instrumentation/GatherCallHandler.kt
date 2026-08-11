// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.java.rt.StreamDebuggerUtils
import com.intellij.debugger.streams.java.rt.gatherers.TracingGathererFactory
import com.sun.jdi.ArrayReference
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import java.util.ArrayList

internal class GatherCallHandler(
  objectStorage: ObjectStorage,
  call: IntermediateStreamCall,
  time: ObjectReference,
) : PeekCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time) {
  private var sourceOffsets: ObjectReference? = null
  private var sourceTimes: ObjectReference? = null

  override fun transformArguments(
    evaluationContextImpl: EvaluationContextImpl,
    method: Method,
    arguments: List<Value?>,
  ): List<Value?> {
    val original = arguments.firstOrNull() as? ObjectReference ?: return arguments
    return objectStorage.watch(evaluationContextImpl) {
      val offsets = instance(ArrayList::class.java)
      val times = instance(ArrayList::class.java)
      sourceOffsets = offsets
      sourceTimes = times

      val factoryClass = helperClass(
        TracingGathererFactory::class.java,
        "com.intellij.debugger.streams.java.rt.gatherers.TracingGathererFactory\$TracingState",
        "com.intellij.debugger.streams.java.rt.gatherers.TracingGathererFactory\$TracingDownstream",
      )
      val wrapMethod = factoryClass.method(
        "wrap",
        "(Ljava/lang/Object;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/List;Ljava/util/List;)Ljava/lang/Object;",
      )
      val wrapped = wrapMethod.invoke(factoryClass, listOf(original, time, offsets, times))
      listOf(wrapped) + arguments.drop(1)
    }
  }

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value {
    val peekResult = super.result(evaluationContextImpl)
    return objectStorage.watch(evaluationContextImpl) {
      array(peekResult, intListToArray(sourceOffsets), intListToArray(sourceTimes))
    }
  }

  private fun ValueContext.intListToArray(values: ObjectReference?): ArrayReference {
    val list = values ?: return array("int", 0)
    val utilsClass = helperClass(StreamDebuggerUtils::class.java)
    val toIntArrayMethod = utilsClass.method("toIntArray", "(Ljava/util/List;)[I")
    return toIntArrayMethod.invoke(utilsClass, listOf(list)) as ArrayReference
  }
}
