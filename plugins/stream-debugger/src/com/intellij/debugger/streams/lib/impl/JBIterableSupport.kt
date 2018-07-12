// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.resolve.AppendResolver
import com.intellij.debugger.streams.resolve.FilteredMapResolver
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctByKeyHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctTraceHandler

/**
 * @author Vitaliy.Bibaev
 */
class JBIterableSupport : LibrarySupportBase() {
  companion object {
    fun filterOperations(vararg names: String): Array<FilterOperation> = names.map { FilterOperation(it) }.toTypedArray()
    fun mapOperations(vararg names: String): Array<MappingOperation> = names.map { MappingOperation(it) }.toTypedArray()
  }

  init {
    addIntermediateOperationsSupport(*filterOperations("filter", "skip", "skipWhile", "take", "takeWhile"))
    addIntermediateOperationsSupport(*mapOperations("map", "transform"))
    addIntermediateOperationsSupport(FlatMappingOperation("flatMap"),
                                     FlatMappingOperation("flatten"))

    addIntermediateOperationsSupport(DistinctOperation("unique") { num, call, dsl ->
      val arguments = call.arguments
      if (arguments.isEmpty()) {
        return@DistinctOperation DistinctTraceHandler(num, call, dsl)
      }
      return@DistinctOperation DistinctByKeyHandler(num, call, dsl, functionApplyName = "fun")
    })

    addIntermediateOperationsSupport(ConcatOperation("append", AppendResolver()))

    addIntermediateOperationsSupport(SortedOperation("sorted"), SortedOperation("collect"))

    addIntermediateOperationsSupport(OrderBasedOperation("filterMap", FilteredMapResolver()))
  }
}