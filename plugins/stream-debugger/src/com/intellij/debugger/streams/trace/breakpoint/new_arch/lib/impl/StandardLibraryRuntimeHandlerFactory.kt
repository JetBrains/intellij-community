// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.impl

import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeHandlerFactory
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeIntermediateCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeSourceCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeTerminalCallHandler
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall

class StandardLibraryRuntimeHandlerFactory : RuntimeHandlerFactory {
  override fun getForSource(): RuntimeSourceCallHandler {
    TODO("Not yet implemented")
  }

  override fun getForIntermediate(call: IntermediateStreamCall): RuntimeIntermediateCallHandler {
    TODO("Not yet implemented")
  }

  override fun getForTermination(call: TerminatorStreamCall): RuntimeTerminalCallHandler {
    TODO("Not yet implemented")
  }

  // TODO: factory
  // Terminal operations:
  // void forEach(Consumer<? super T> action)
  // void forEachOrdered(Consumer<? super T> action)

  // Object[] toArray()
  // <A> A[] toArray(IntFunction<A[]> generator)
  // T reduce(T identity, BinaryOperator<T> accumulator)
  // <U> U reduce(U identity, BiFunction<U,? super T,U> accumulator, BinaryOperator<U> combiner)
  // <R> R collect(Supplier<R> supplier, BiConsumer<R,? super T> accumulator, BiConsumer<R,R> combiner)
  // <R,A> R collect(Collector<? super T,A,R> collector)
  // long count()
  // default List<T> toList()

  // boolean anyMatch(Predicate<? super T> predicate)
  // boolean allMatch(Predicate<? super T> predicate)
  // boolean noneMatch(Predicate<? super T> predicate)

  // Optional<T> reduce(BinaryOperator<T> accumulator)
  // Optional<T> min(Comparator<? super T> comparator)
  // Optional<T> max(Comparator<? super T> comparator)
  // Optional<T> findFirst()
  // Optional<T> findAny()
  //private fun getTerminationOperationFormatter(valueManager: ValueManager, evaluationContext: EvaluationContextImpl, streamCall: StreamCall): TerminationOperationTraceFormatter = when(streamCall.name) {
  //  "findAny", "findFirst", "min", "max" -> OptionalTraceFormatter(valueManager, evaluationContext)
  //  "forEach", "forEachOrdered" -> ForEachTraceFormatter(valueManager, evaluationContext)
  //  "anyMatch", "allMatch", "noneMatch" -> TODO("Not implemented")
  //  else -> ToCollectionTraceFormatter(valueManager, evaluationContext)
  //}
}