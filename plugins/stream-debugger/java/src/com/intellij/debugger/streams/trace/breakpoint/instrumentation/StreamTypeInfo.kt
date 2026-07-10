// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException
import com.intellij.psi.CommonClassNames

internal const val UNIVERSAL_COLLECTOR_CONSTRUCTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Z)V"

// Matcher constructor signatures: (Map beforeMap, Map afterMap, AtomicInteger time, <Predicate>) -> void
internal const val INT_MATCHER_CONSTRUCTOR_SIGNATURE    = "(Ljava/util/Map;Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/IntPredicate;)V"
internal const val LONG_MATCHER_CONSTRUCTOR_SIGNATURE   = "(Ljava/util/Map;Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/LongPredicate;)V"
internal const val DOUBLE_MATCHER_CONSTRUCTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/DoublePredicate;)V"
internal const val OBJECT_MATCHER_CONSTRUCTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/Predicate;)V"

// Predicate type names (used to dispatch to the correct Matcher)
internal const val JAVA_UTIL_FUNCTION_PREDICATE        = "java.util.function.Predicate"
internal const val JAVA_UTIL_FUNCTION_INT_PREDICATE    = "java.util.function.IntPredicate"
internal const val JAVA_UTIL_FUNCTION_LONG_PREDICATE   = "java.util.function.LongPredicate"
internal const val JAVA_UTIL_FUNCTION_DOUBLE_PREDICATE = "java.util.function.DoublePredicate"

// Optional type names (used in OptionalRuntimeHandler)
internal const val JAVA_UTIL_OPTIONAL        = "java.util.Optional"
internal const val JAVA_UTIL_OPTIONAL_INT    = "java.util.OptionalInt"
internal const val JAVA_UTIL_OPTIONAL_LONG   = "java.util.OptionalLong"
internal const val JAVA_UTIL_OPTIONAL_DOUBLE = "java.util.OptionalDouble"

/**
 * Information specific to stream type for instrumentation.
 * Handles differences between Object/Int/Long/Double streams.
 */
internal enum class StreamTypeInfo(
  val peekSignature: String,
  val formatterMethod: String
) {
  ObjectStream(
    peekSignature = "(Ljava/util/function/Consumer;)Ljava/util/stream/Stream;",
    formatterMethod = "formatObjectMap"
  ),
  IntStream(
    peekSignature = "(Ljava/util/function/IntConsumer;)Ljava/util/stream/IntStream;",
    formatterMethod = "formatIntMap"
  ),
  LongStream(
    peekSignature = "(Ljava/util/function/LongConsumer;)Ljava/util/stream/LongStream;",
    formatterMethod = "formatLongMap"
  ),
  DoubleStream(
    peekSignature = "(Ljava/util/function/DoubleConsumer;)Ljava/util/stream/DoubleStream;",
    formatterMethod = "formatDoubleMap"
  );

  companion object {
    fun forType(typeName: String): StreamTypeInfo = when (typeName) {
      CommonClassNames.JAVA_LANG_OBJECT -> ObjectStream
      CommonClassNames.JAVA_LANG_INTEGER -> IntStream
      CommonClassNames.JAVA_LANG_LONG -> LongStream
      CommonClassNames.JAVA_LANG_DOUBLE -> DoubleStream
      else -> throw BreakpointTracingException("Could not get collector for stream of type $typeName")
    }
  }
}
