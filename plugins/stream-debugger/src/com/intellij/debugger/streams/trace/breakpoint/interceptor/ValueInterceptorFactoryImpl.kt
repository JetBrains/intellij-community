// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.interceptor

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInstantiationException
import com.intellij.psi.CommonClassNames
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

const val ATOMIC_INTEGER_CLASS_NAME = "java.util.concurrent.atomic.AtomicInteger"

const val JAVA_UTIL_FUNCTION_CONSUMER = "java.util.function.Consumer"
const val JAVA_UTIL_FUNCTION_INT_CONSUMER = "java.util.function.IntConsumer"
const val JAVA_UTIL_FUNCTION_LONG_CONSUMER = "java.util.function.LongConsumer"
const val JAVA_UTIL_FUNCTION_DOUBLE_CONSUMER = "java.util.function.DoubleConsumer"

const val OBJECT_COLLECTOR_CLASS_NAME = "com.intellij.debugger.stream.rt.java.collectors.ObjectCollector"
const val INT_COLLECTOR_CLASS_NAME = "com.intellij.debugger.stream.rt.java.collectors.IntCollector"
const val LONG_COLLECTOR_CLASS_NAME = "com.intellij.debugger.stream.rt.java.collectors.LongCollector"
const val DOUBLE_COLLECTOR_CLASS_NAME = "com.intellij.debugger.stream.rt.java.collectors.DoubleCollector"

const val OBJECT_COLLECTOR_CLASS_FILE = "com/intellij/debugger/stream/rt/java/collectors/ObjectCollector.class"
const val INT_COLLECTOR_CLASS_FILE = "com/intellij/debugger/stream/rt/java/collectors/IntCollector.class"
const val LONG_COLLECTOR_CLASS_FILE = "com/intellij/debugger/stream/rt/java/collectors/LongCollector.class"
const val DOUBLE_COLLECTOR_CLASS_FILE = "com/intellij/debugger/stream/rt/java/collectors/DoubleCollector.class"

const val COLLECTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;)V"

class ValueInterceptorFactoryImpl(private val valueManager: ValueManager,
                                  evaluationContext: EvaluationContextImpl) : ValueInterceptorFactory {
  private lateinit var counterObject: ObjectReference
  private lateinit var elapsedTime: ArrayReference

  init {
    valueManager.watch(evaluationContext) {
      counterObject = instance(ATOMIC_INTEGER_CLASS_NAME)
      elapsedTime = array("long", 1)
        .apply { setValue(0, 0L.mirror) }
    }
  }

  private val valueStorages: MutableList<ObjectReference> = mutableListOf()
  private var streamResult: Value? = null

  override val collectedValues: StreamTraceValues
    get() = if (streamResult == null)
      throw BreakpointTracingException("Stream result was not collected")
    else StreamTraceValues(
      valueStorages,
      streamResult!!,
      counterObject,
      elapsedTime
    )

  override fun getForIntermediate(evaluationContext: EvaluationContextImpl, collectorType: String): ValueInterceptor = valueManager.watch(
    evaluationContext) {
    val mapInstance = instance(CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP)
    valueStorages.add(mapInstance)

    val collectorClassName = getCollectorClass(collectorType)
    val collectorMirror = instance(collectorClassName, COLLECTOR_SIGNATURE, listOf(mapInstance, counterObject))
    PeekValueInterceptor(collectorMirror, collectorType)
  }

  override fun getForTermination(evaluationContext: EvaluationContextImpl): ValueInterceptor = ValueInterceptor { _, value ->
    streamResult = value
    return@ValueInterceptor value
  }

  private fun getCollectorClass(requestedType: String) = when (requestedType) {
    JAVA_UTIL_FUNCTION_CONSUMER -> OBJECT_COLLECTOR_CLASS_NAME
    JAVA_UTIL_FUNCTION_INT_CONSUMER -> INT_COLLECTOR_CLASS_NAME
    JAVA_UTIL_FUNCTION_LONG_CONSUMER -> LONG_COLLECTOR_CLASS_NAME
    JAVA_UTIL_FUNCTION_DOUBLE_CONSUMER -> DOUBLE_COLLECTOR_CLASS_NAME
    else -> throw ValueInstantiationException(requestedType)
  }
}