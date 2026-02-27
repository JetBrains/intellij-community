// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.trace.impl.interpret.ex.UnexpectedValueTypeException
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

/**
 * Handles terminal operations returning an [Optional][java.util.Optional] result:
 * `min`, `max`, `findAny`, `findFirst`.
 *
 * Extends [PeekTerminalCallHandler] for stream element collection, then additionally
 * unwraps the Optional result to expose both `isPresent` and the unwrapped value.
 *
 * Result structure:
 * ```
 * [
 *   [
 *     [beforeArray, afterArray],            // beforeAfter from rawResult
 *     [[isPresent: boolean[]], [value]]     // Optional-specific info
 *   ],
 *   wrappedResult                           // the Optional itself
 * ]
 * ```
 */
internal class OptionalRuntimeHandler(
  call: TerminatorStreamCall,
  objectStorage: ObjectStorage,
  time: ObjectReference,
) : PeekTerminalCallHandler(objectStorage, call.getTypeBefore(), null, time) {

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val (peekResult, wrappedResult) = rawResult(evaluationContextImpl)
      val optional = wrappedResult.getValue(0) as ObjectReference
      assertIsOptional(optional)

      val isPresent = optional.referenceType().method("isPresent", "()Z").invoke(optional)
      val unwrapped = unwrapOptionalOrDefault(optional)
      val beforeAfter = peekResult.getValue(0)

      array(
        array(
          beforeAfter,
          array(
            array(isPresent),
            array(unwrapped)
          )
        ),
        wrappedResult
      )
    }
  }

  private fun ValueContext.unwrapOptionalOrDefault(optional: ObjectReference): Value? {
    val typeName = optional.referenceType().name()
    val orElseSignature = getOrElseSignature(typeName)
    val orElseMethod = optional.referenceType().method("orElse", orElseSignature)
    val orElseArg = orElseMethod.argumentTypes().first().defaultValue()
    return orElseMethod.invoke(optional, listOf(orElseArg))
  }

  private fun getOrElseSignature(typeName: String): String = when (typeName) {
    JAVA_UTIL_OPTIONAL -> "(Ljava/lang/Object;)Ljava/lang/Object;"
    JAVA_UTIL_OPTIONAL_INT -> "(I)I"
    JAVA_UTIL_OPTIONAL_LONG -> "(J)J"
    JAVA_UTIL_OPTIONAL_DOUBLE -> "(D)D"
    else -> throw UnexpectedValueTypeException("Expected Optional but got $typeName")
  }

  private fun assertIsOptional(value: ObjectReference) {
    if (!isOptional(value.referenceType())) {
      throw UnexpectedValueTypeException("Optional expected but got ${value.referenceType().name()}")
    }
  }

  private fun isOptional(referenceType: ReferenceType): Boolean = when (referenceType.name()) {
    JAVA_UTIL_OPTIONAL, JAVA_UTIL_OPTIONAL_INT, JAVA_UTIL_OPTIONAL_LONG, JAVA_UTIL_OPTIONAL_DOUBLE -> true
    else -> false
  }
}
