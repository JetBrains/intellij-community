// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.trace.impl.interpret.ex.UnexpectedValueTypeException
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.java.rt.matchers.DoubleMatcher
import com.intellij.debugger.streams.java.rt.matchers.IntMatcher
import com.intellij.debugger.streams.java.rt.matchers.LongMatcher
import com.intellij.debugger.streams.java.rt.matchers.ObjectMatcher
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Handles match terminal operations: `anyMatch`, `allMatch`, `noneMatch`.
 *
 * Instead of wrapping the stream with peek collectors, replaces the predicate argument
 * with a Matcher RT class that internally collects before/after values as each element
 * is tested.
 *
 * Transformation example for `anyMatch(x -> x < 0)`:
 * ```java
 * // Before
 * .anyMatch(x -> x < 0)
 * // After
 * .anyMatch(new IntMatcher(beforeMap, afterMap, time, x -> x < 0))
 * ```
 *
 * Result structure:
 * ```
 * [
 *   [[beforeFormatted, afterFormatted], [matchResult]],
 *   [matchResult]
 * ]
 * ```
 */
internal class MatchRuntimeHandler(
  private val call: TerminatorStreamCall,
  private val objectStorage: ObjectStorage,
  private val time: ObjectReference,
) : TerminalCallHandler {

  private var beforeValuesMap: ObjectReference? = null
  private var afterValuesMap: ObjectReference? = null
  private var streamResult: Value? = null

  override fun transformArguments(
    evaluationContextImpl: EvaluationContextImpl,
    method: Method,
    arguments: List<Value?>,
  ): List<Value?> = objectStorage.watch(evaluationContextImpl) {
    val argumentTypes = method.argumentTypes()
    require(argumentTypes.size == 1) { "Match operator should accept exactly one predicate" }

    val predicate = arguments.first()
    val predicateTypeName = argumentTypes.first().name()
    val (matcherClass, matcherSig) = matcherForType(predicateTypeName)

    val before = instance(LinkedHashMap::class.java)
    val after = instance(LinkedHashMap::class.java)
    beforeValuesMap = before
    afterValuesMap = after

    val matcher = instance(matcherClass, matcherSig, listOf(before, after, time, predicate))
    listOf(matcher)
  }

  override fun beforeCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = value

  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    if (value is ObjectReference) {
      objectStorage.store(value)
    }
    streamResult = value
    return value
  }

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value =
    objectStorage.watch(evaluationContextImpl) {
      val typeBefore = call.getTypeBefore()
      val beforeFormatted = beforeValuesMap?.let { formatMap(it, typeBefore) } ?: emptyResult()
      val afterFormatted = afterValuesMap?.let { formatMap(it, typeBefore) } ?: emptyResult()

      val info = array(beforeFormatted, afterFormatted)
      val result = array(streamResult)

      // Match interpreter expects: [[info, result], result]
      array(array(info, result), result)
    }

  private fun matcherForType(predicateTypeName: String): Pair<Class<*>, String> = when (predicateTypeName) {
    JAVA_UTIL_FUNCTION_INT_PREDICATE    -> Pair(IntMatcher::class.java, INT_MATCHER_CONSTRUCTOR_SIGNATURE)
    JAVA_UTIL_FUNCTION_LONG_PREDICATE   -> Pair(LongMatcher::class.java, LONG_MATCHER_CONSTRUCTOR_SIGNATURE)
    JAVA_UTIL_FUNCTION_DOUBLE_PREDICATE -> Pair(DoubleMatcher::class.java, DOUBLE_MATCHER_CONSTRUCTOR_SIGNATURE)
    JAVA_UTIL_FUNCTION_PREDICATE        -> Pair(ObjectMatcher::class.java, OBJECT_MATCHER_CONSTRUCTOR_SIGNATURE)
    else -> throw UnexpectedValueTypeException("Expected Predicate but got $predicateTypeName")
  }
}
