// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.formatter

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueContext
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.interceptor.StreamTraceValues
import com.intellij.debugger.streams.trace.breakpoint.ex.TypeException
import com.intellij.debugger.streams.trace.impl.interpret.ex.UnexpectedValueTypeException
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.intellij.psi.CommonClassNames.JAVA_UTIL_OPTIONAL
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

/**
 * @author Shumaf Lovpache
 */
class OptionalTraceFormatter(
  private val valueManager: ValueManager,
  private val evaluationContext: EvaluationContextImpl
) : TraceFormatterBase(valueManager, evaluationContext), TerminationOperationTraceFormatter {
  override fun format(streamCall: TerminatorStreamCall, collectedValues: StreamTraceValues, beforeValues: Value?, afterValues: Value?): Value {
    assertIsOptional(collectedValues.streamResult)
    val beforeAfter = formatBeforeAfter(beforeValues, streamCall.typeBefore.variableTypeName, afterValues, streamCall.resultType.variableTypeName)
    return valueManager.watch(evaluationContext) {
      val streamResult = collectedValues.streamResult as ObjectReference
      val optionalType = streamResult.type() as ReferenceType
      val isPresentMethod = optionalType.method("isPresent", "()Z")
      val isPresent = isPresentMethod.invoke(streamResult, emptyList())

      val optionalContent = getOptionalContent(streamResult)

      array(
        beforeAfter,
        array(
          array(isPresent),
          array(optionalContent)
        )
      )
    }
  }

  private fun ValueContext.getOptionalContent(streamResult: ObjectReference): Value? {
    val optionalType = streamResult.referenceType()
    val orElseMethod = streamResult.method("orElse", orElseSignature(optionalType))
    val orElseArg = orElseMethod.argumentTypes().first().defaultValue()
    return orElseMethod.invoke(streamResult, listOf(orElseArg))
  }

  private fun orElseSignature(optionalType: ReferenceType) = when(optionalType.name()) {
    JAVA_UTIL_OPTIONAL -> "(Ljava/lang/Object;)Ljava/lang/Object;"
    "java.util.OptionalInt" -> "(I)I"
    "java.util.OptionalLong" -> "(J)J"
    "java.util.OptionalDouble" -> "(D)D"
    else -> throw TypeException("Expected Optional but got ${optionalType.name()}")
  }

  private fun isOptional(optionalType: ReferenceType) = when(optionalType.name()) {
    JAVA_UTIL_OPTIONAL, "java.util.OptionalInt",
    "java.util.OptionalLong", "java.util.OptionalDouble" -> true
    else -> false
  }

  private fun assertIsOptional(value: Value) {
    if (value is ObjectReference && isOptional(value.referenceType())) return
    throw UnexpectedValueTypeException("Optional expected. But ${value.type().name()} received")
  }
}