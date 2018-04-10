// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.IntermediateOperation
import com.intellij.debugger.streams.resolve.AppendResolver
import com.intellij.debugger.streams.resolve.IntervalMapResolver
import com.intellij.debugger.streams.resolve.PairMapResolver
import com.intellij.debugger.streams.resolve.PrependResolver
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctByKeyHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctKeysHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctTraceHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctValuesHandler


/**
 * @author Vitaliy.Bibaev
 */
class StreamExLibrarySupport
  : LibrarySupportBase(StandardLibrarySupport()) {
  init {
    addIntermediateOperationsSupport(*filterOperations(
      "atLeast", "atMost", "less", "greater", "filterBy", "filterKeys", "filterValues", "filterKeyValue",
      "nonNull", "nonNullKeys", "nonNullValues", "remove", "removeBy", "removeKeys", "removeValues", "removeKeyValue",
      "select", "selectKeys", "selectValues", "dropWhile", "takeWhile", "takeWhileInclusive", "skipOrdered",
      "without", "peekFirst", "peekLast", "peekKeys", "peekValues", "peekKeyValue"))

    addIntermediateOperationsSupport(*mapOperations(
      "mapFirst", "mapFirstOrElse", "mapLast", "mapLastOrElse",
      "keys", "values", "mapKeyValue", "mapKeys", "mapValues", "mapToEntry", "mapToKey", "mapToValue",
      "elements", "invert", "join", "withFirst", "zipWith"))

    addIntermediateOperationsSupport(*flatMapOperations(
      "flatMapToInt", "flatMapToLong", "flatMapToDouble", "flatMapToObj", "flatMapToEntry", "cross",
      "flatMapToKey", "flatMapToValue", "flatMapKeys", "flatMapValues", "flatMapKeyValue", "flatArray", "flatCollection"))

    addIntermediateOperationsSupport(*sortedOperations("sortedBy", "sortedByInt", "sortedByDouble", "sortedByLong", "reverseSorted"))

    addIntermediateOperationsSupport(
      DistinctOperation("distinct", { num, call,dsl ->
        val arguments = call.arguments
        if (arguments.isEmpty() || arguments[0].type == "int") {
          return@DistinctOperation DistinctTraceHandler(num, call, dsl)
        }
        return@DistinctOperation DistinctByKeyHandler(num, call, dsl)
      }),
      DistinctOperation("distinctKeys", { num, call, dsl -> DistinctKeysHandler(num, call, dsl) }),
      DistinctOperation("distinctValues", { num, call, dsl -> DistinctValuesHandler(num, call, dsl) })
    )

    addIntermediateOperationsSupport(ConcatOperation("append", AppendResolver()),
                                     ConcatOperation("prepend", PrependResolver()))

    addIntermediateOperationsSupport(*collapseOperations("collapse", "collapseKeys", "runLengths", "groupRuns"))

    addIntermediateOperationsSupport(OrderBasedOperation("pairMap", PairMapResolver()),
                                     OrderBasedOperation("intervalMap", IntervalMapResolver()))
    addTerminationOperationsSupport()
  }

  private fun filterOperations(vararg names: String): Array<IntermediateOperation> = names.map { FilterOperation(it) }.toTypedArray()

  private fun mapOperations(vararg names: String): Array<IntermediateOperation> = names.map { MappingOperation(it) }.toTypedArray()

  private fun flatMapOperations(vararg names: String): Array<IntermediateOperation> = names.map { FlatMappingOperation(it) }.toTypedArray()

  private fun sortedOperations(vararg names: String): Array<IntermediateOperation> = names.map { SortedOperation(it) }.toTypedArray()

  private fun collapseOperations(vararg names: String): Array<IntermediateOperation> = names.map { CollapseOperation(it) }.toTypedArray()
}