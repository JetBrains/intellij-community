// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.formatter

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.interceptor.StreamTraceValues
import com.intellij.debugger.streams.wrapper.StreamCall
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.sun.jdi.ArrayReference
import com.sun.jdi.VoidValue

class StreamTraceFormatterImpl(private val valueManager: ValueManager) : StreamTraceFormatter {
  // TODO: use value formatter factory
  override fun formatTraceResult(chain: StreamChain,
                                 collectedValues: StreamTraceValues,
                                 evaluationContext: EvaluationContextImpl): ArrayReference {
    val intermediateStepsValues = collectedValues.intermediateOperationValues
    return valueManager.watch(evaluationContext) {
      val peekFormatter : IntermediateOperationTraceFormatter = PeekTraceFormatter(valueManager, evaluationContext)
      val terminatorFormatter : TerminationOperationTraceFormatter = getTerminationOperationFormatter(valueManager, evaluationContext, chain.terminationCall)
      // Переделать сейчас работает только с терминалом collector
      val formattedIntermediateTraces = intermediateStepsValues
        .zipWithNext()
        .zip(chain.intermediateCalls)
        .map { (p, streamCall) -> peekFormatter.format(streamCall, p.first, p.second) }
      val formattedTerminatorTrace = terminatorFormatter.format(chain.terminationCall, collectedValues, intermediateStepsValues.last(), null)
      val infoArray = array(formattedIntermediateTraces + formattedTerminatorTrace)

      val streamResult = if (collectedValues.streamResult is VoidValue) {
        array(JAVA_LANG_OBJECT, 1)
      } else {
        array(collectedValues.streamResult)
      }

      array(
        infoArray,
        streamResult,
        collectedValues.elapsedTime
      )
    }
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
  private fun getTerminationOperationFormatter(valueManager: ValueManager, evaluationContext: EvaluationContextImpl, streamCall: StreamCall): TerminationOperationTraceFormatter = when(streamCall.name) {
    "findAny", "findFirst", "min", "max" -> OptionalTraceFormatter(valueManager, evaluationContext)
    "forEach", "forEachOrdered" -> ForEachTraceFormatter(valueManager, evaluationContext)
    "anyMatch", "allMatch", "noneMatch" -> TODO("Not implemented")
    else -> ToCollectionTraceFormatter(valueManager, evaluationContext)
  }
}