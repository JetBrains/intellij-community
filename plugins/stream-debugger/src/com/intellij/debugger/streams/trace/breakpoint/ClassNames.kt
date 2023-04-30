// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

const val ATOMIC_INTEGER_CLASS_NAME = "java.util.concurrent.atomic.AtomicInteger"

const val JAVA_UTIL_FUNCTION_CONSUMER = "java.util.function.Consumer"
const val JAVA_UTIL_FUNCTION_INT_CONSUMER = "java.util.function.IntConsumer"
const val JAVA_UTIL_FUNCTION_LONG_CONSUMER = "java.util.function.LongConsumer"
const val JAVA_UTIL_FUNCTION_DOUBLE_CONSUMER = "java.util.function.DoubleConsumer"

const val INT_COLLECTOR_CLASS_NAME = "com.intellij.debugger.stream.rt.java.collectors.IntCollector"
const val LONG_COLLECTOR_CLASS_NAME = "com.intellij.debugger.stream.rt.java.collectors.LongCollector"
const val DOUBLE_COLLECTOR_CLASS_NAME = "com.intellij.debugger.stream.rt.java.collectors.DoubleCollector"
const val OBJECT_COLLECTOR_CLASS_NAME = "com.intellij.debugger.stream.rt.java.collectors.ObjectCollector"

const val INT_COLLECTOR_CLASS_FILE = "com/intellij/debugger/stream/rt/java/collectors/IntCollector.class"
const val LONG_COLLECTOR_CLASS_FILE = "com/intellij/debugger/stream/rt/java/collectors/LongCollector.class"
const val DOUBLE_COLLECTOR_CLASS_FILE = "com/intellij/debugger/stream/rt/java/collectors/DoubleCollector.class"
const val OBJECT_COLLECTOR_CLASS_FILE = "com/intellij/debugger/stream/rt/java/collectors/ObjectCollector.class"

const val COLLECTOR_CONSTRUCTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;)V"

const val OBJECT_CONSUMER_SIGNATURE = "(Ljava/util/function/Consumer;)Ljava/util/stream/Stream;"
const val INT_CONSUMER_SIGNATURE = "(Ljava/util/function/IntConsumer;)Ljava/util/stream/IntStream;"
const val LONG_CONSUMER_SIGNATURE = "(Ljava/util/function/LongConsumer;)Ljava/util/stream/LongStream;"
const val DOUBLE_CONSUMER_SIGNATURE = "(Ljava/util/function/DoubleConsumer;)Ljava/util/stream/DoubleStream;"
