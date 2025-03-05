// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.lib.sequence

import com.intellij.debugger.streams.core.lib.IntermediateOperation
import com.intellij.debugger.streams.core.lib.impl.*
import com.intellij.debugger.streams.core.resolve.AppendResolver
import com.intellij.debugger.streams.core.resolve.ChunkedResolver
import com.intellij.debugger.streams.core.resolve.PairMapResolver
import com.intellij.debugger.streams.core.trace.impl.handler.unified.DistinctTraceHandler
import com.intellij.debugger.streams.core.trace.impl.interpret.SimplePeekCallTraceInterpreter
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.debugger.sequence.resolve.FilteredMapResolver
import org.jetbrains.kotlin.idea.debugger.sequence.resolve.WindowedResolver
import org.jetbrains.kotlin.idea.debugger.sequence.trace.impl.handler.sequence.FilterIsInstanceHandler
import org.jetbrains.kotlin.idea.debugger.sequence.trace.impl.handler.sequence.KotlinDistinctByHandler

class KotlinSequencesSupport : LibrarySupportBase() {
    init {
        addIntermediateOperationsSupport(
            *filterOperations(
                "filter", "filterNot", "filterIndexed",
                "drop", "dropWhile", "minus", "minusElement", "take", "takeWhile", "onEach", "asSequence"
            )
        )

        addIntermediateOperationsSupport(FilterIsInstanceOperationHandler())

        addIntermediateOperationsSupport(
            *mapOperations(
                "map", "mapIndexed", "requireNoNulls", "withIndex",
                "zip", "constrainOnce"
            )
        )

        addIntermediateOperationsSupport(*flatMapOperations("flatMap", "flatten"))

        addIntermediateOperationsSupport(*sortedOperations("sorted", "sortedBy", "sortedDescending", "sortedWith"))

        addIntermediateOperationsSupport(DistinctOperation("distinct", ::DistinctTraceHandler))
        addIntermediateOperationsSupport(DistinctOperation("distinctBy", ::KotlinDistinctByHandler))

        addIntermediateOperationsSupport(ConcatOperation("plus", AppendResolver()))
        addIntermediateOperationsSupport(ConcatOperation("plusElement", AppendResolver()))

        addIntermediateOperationsSupport(OrderBasedOperation("zipWithNext", PairMapResolver()))

        addIntermediateOperationsSupport(OrderBasedOperation("mapNotNull", FilteredMapResolver()))
        addIntermediateOperationsSupport(OrderBasedOperation("chunked", ChunkedResolver()))
        addIntermediateOperationsSupport(OrderBasedOperation("windowed", WindowedResolver()))
    }

    private fun filterOperations(@NonNls vararg names: String): Array<IntermediateOperation> =
        names.map { FilterOperation(it) }.toTypedArray()

    private fun mapOperations(@NonNls vararg names: String): Array<IntermediateOperation> =
        names.map { MappingOperation(it) }.toTypedArray()

    private fun flatMapOperations(@NonNls vararg names: String): Array<IntermediateOperation> =
        names.map { FlatMappingOperation(it) }.toTypedArray()

    private fun sortedOperations(@NonNls vararg names: String): Array<IntermediateOperation> =
        names.map { SortedOperation(it) }.toTypedArray()

    private class FilterIsInstanceOperationHandler : IntermediateOperationBase(
        "filterIsInstance", ::FilterIsInstanceHandler,
        SimplePeekCallTraceInterpreter(), FilteredMapResolver()
    )
}