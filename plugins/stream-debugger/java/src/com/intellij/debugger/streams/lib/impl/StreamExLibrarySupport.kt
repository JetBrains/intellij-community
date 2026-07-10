// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.IntermediateOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedDistinctMapEntryOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedDistinctOperation
import com.intellij.debugger.streams.core.resolve.AppendResolver
import com.intellij.debugger.streams.core.resolve.IntervalMapResolver
import com.intellij.debugger.streams.core.resolve.PairMapResolver
import com.intellij.debugger.streams.core.resolve.PrependResolver
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedCollapseOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedConcatOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedFilterOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedFlatMappingOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedMappingOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedOrderBasedOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedSortedOperation


/**
 * @author Vitaliy.Bibaev
 */
class StreamExLibrarySupport
  : JvmLibrarySupportBase(StandardLibrarySupport()) {
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
      BreakpointBasedDistinctOperation("distinct"),
      BreakpointBasedDistinctMapEntryOperation.keys("distinctKeys"),
      BreakpointBasedDistinctMapEntryOperation.values("distinctValues"),
    )

    addIntermediateOperationsSupport(BreakpointBasedConcatOperation("append", AppendResolver()),
                                     BreakpointBasedConcatOperation("prepend", PrependResolver()))

    addIntermediateOperationsSupport(*collapseOperations("collapse", "collapseKeys", "runLengths", "groupRuns"))

    addIntermediateOperationsSupport(BreakpointBasedOrderBasedOperation("pairMap", PairMapResolver()),
                                     BreakpointBasedOrderBasedOperation("intervalMap", IntervalMapResolver()))
    addTerminationOperationsSupport()
  }

  private fun filterOperations(vararg names: String): Array<IntermediateOperation> = names.map { BreakpointBasedFilterOperation(it) }.toTypedArray()

  private fun mapOperations(vararg names: String): Array<IntermediateOperation> = names.map { BreakpointBasedMappingOperation(it) }.toTypedArray()

  private fun flatMapOperations(vararg names: String): Array<IntermediateOperation> = names.map { BreakpointBasedFlatMappingOperation(it) }.toTypedArray()

  private fun sortedOperations(vararg names: String): Array<IntermediateOperation> = names.map { BreakpointBasedSortedOperation(it) }.toTypedArray()

  private fun collapseOperations(vararg names: String): Array<IntermediateOperation> = names.map { BreakpointBasedCollapseOperation(it) }.toTypedArray()
}