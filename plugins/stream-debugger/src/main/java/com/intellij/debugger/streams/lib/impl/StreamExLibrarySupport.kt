/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.IntermediateOperation
import com.intellij.debugger.streams.resolve.AppendResolver
import com.intellij.debugger.streams.resolve.IntervalMapResolver
import com.intellij.debugger.streams.resolve.PairMapResolver
import com.intellij.debugger.streams.resolve.PrependResolver
import com.intellij.debugger.streams.trace.impl.handler.DistinctByKeyHandler
import com.intellij.debugger.streams.trace.impl.handler.DistinctHandler
import com.intellij.debugger.streams.trace.impl.handler.DistinctKeysHandler
import com.intellij.debugger.streams.trace.impl.handler.DistinctValuesHandler
import com.intellij.openapi.project.Project


/**
 * @author Vitaliy.Bibaev
 */
class StreamExLibrarySupport(project: Project)
  : LibrarySupportBase(LibraryImpl("StreamEx", JavaLanguage(project), "one.util.stream"),
                       StandardLibrarySupport(project)) {
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
      DistinctOperation("distinct", { num, call ->
        val arguments = call.arguments
        if (arguments.isEmpty() || arguments[0].type == "int") {
          return@DistinctOperation DistinctHandler(num, call)
        }
        return@DistinctOperation DistinctByKeyHandler(num, call)
      }),
      DistinctOperation("distinctKeys", ::DistinctKeysHandler),
      DistinctOperation("distinctValues", ::DistinctValuesHandler)
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