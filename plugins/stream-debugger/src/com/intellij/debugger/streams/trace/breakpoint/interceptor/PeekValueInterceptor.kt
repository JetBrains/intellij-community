// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.interceptor

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ex.ArgumentTypeMismatchException
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInterceptionException
import com.sun.jdi.ClassType
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import kotlin.reflect.KClass

const val OBJECT_CONSUMER_SIGNATURE = "(Ljava/util/function/Consumer;)Ljava/util/stream/Stream;"
const val INT_CONSUMER_SIGNATURE = "(Ljava/util/function/IntConsumer;)Ljava/util/stream/IntStream;"
const val LONG_CONSUMER_SIGNATURE = "(Ljava/util/function/LongConsumer;)Ljava/util/stream/LongStream;"
const val DOUBLE_CONSUMER_SIGNATURE = "(Ljava/util/function/DoubleConsumer;)Ljava/util/stream/DoubleStream;"

/**
 * @author Shumaf Lovpache
 */
class PeekValueInterceptor(private val collectorMirror: ObjectReference, private val collectorType: String): ValueInterceptor {
  override fun intercept(evaluationContext: EvaluationContextImpl, value: Value): Value {
    if (value !is ObjectReference) createTypeMismatchException(value, ObjectReference::class)

    val peekArgs = listOf(collectorMirror)

    val peekSignature = peekSignature()
    val valueType = value.referenceType() as ClassType
    val peekMethod = DebuggerUtils.findMethod(valueType, "peek", peekSignature)
                     ?: throw MethodNotFoundException("peek", peekSignature, valueType.name())

    if (!checkStreamMethodArguments(peekMethod, peekArgs)) throw ArgumentTypeMismatchException(peekMethod, peekArgs)

    return evaluationContext.debugProcess
      .invokeInstanceMethod(evaluationContext, value, peekMethod, peekArgs, 0, true)
  }

  private fun checkStreamMethodArguments(vmMethod: Method, actualArgs: List<Value>): Boolean =
    vmMethod.argumentTypes().size == actualArgs.size && vmMethod.argumentTypes()
      .zip(actualArgs).all { (expectedArgType, actualArg) -> DebuggerUtils.instanceOf(actualArg.type(), expectedArgType.name()) }

  private fun createTypeMismatchException(value: Value, expectedType: KClass<ObjectReference>): Nothing = throw ValueInterceptionException(
    "Cannot modify value because it is of type ${value.type().name()} " +
    "(expected value of type ${expectedType.simpleName}) "
  )

  private fun peekSignature() = when (collectorType) {
    JAVA_UTIL_FUNCTION_CONSUMER -> OBJECT_CONSUMER_SIGNATURE
    JAVA_UTIL_FUNCTION_INT_CONSUMER -> INT_CONSUMER_SIGNATURE
    JAVA_UTIL_FUNCTION_LONG_CONSUMER -> LONG_CONSUMER_SIGNATURE
    JAVA_UTIL_FUNCTION_DOUBLE_CONSUMER -> DOUBLE_CONSUMER_SIGNATURE
    else -> throw MethodNotFoundException("peek", "Ljava/util/function/Consumer;",collectorType)
  }
}