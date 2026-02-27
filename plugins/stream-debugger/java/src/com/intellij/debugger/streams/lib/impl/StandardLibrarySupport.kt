// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.trace.impl.interpret.AllMatchTraceInterpreter
import com.intellij.debugger.streams.core.trace.impl.interpret.AnyMatchTraceInterpreter
import com.intellij.debugger.streams.core.trace.impl.interpret.NoneMatchTraceInterpreter
import com.intellij.debugger.streams.trace.breakpoint.BreakpointPositionResolver
import com.intellij.debugger.streams.trace.breakpoint.JavaBreakpointPositionResolver
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedFilterOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedFlatMappingOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedMappingOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedMatchingOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedOptionalResultOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedDistinctOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedParallelOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedSortedOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedToCollectionOperation

/**
 * @author Vitaliy.Bibaev
 */
class StandardLibrarySupport : JvmLibrarySupportBase() {
  init {
    addIntermediateOperationsSupport(BreakpointBasedFilterOperation("filter"),
                                     BreakpointBasedFilterOperation("limit"),
                                     BreakpointBasedFilterOperation("skip"),
                                     BreakpointBasedFilterOperation("peek"),
                                     BreakpointBasedFilterOperation("onClose"),
                                     BreakpointBasedMappingOperation("map"),
                                     BreakpointBasedMappingOperation("mapToInt"),
                                     BreakpointBasedMappingOperation("mapToLong"),
                                     BreakpointBasedMappingOperation("mapToDouble"),
                                     BreakpointBasedMappingOperation("mapToObj"),
                                     BreakpointBasedMappingOperation("boxed"),
                                     BreakpointBasedFlatMappingOperation("flatMap"),
                                     BreakpointBasedFlatMappingOperation("flatMapToInt"),
                                     BreakpointBasedFlatMappingOperation("flatMapToLong"),
                                     BreakpointBasedFlatMappingOperation("flatMapToDouble"),
                                     BreakpointBasedDistinctOperation("distinct"),
                                     BreakpointBasedSortedOperation("sorted"),
                                     BreakpointBasedParallelOperation("parallel"))

    addTerminationOperationsSupport(BreakpointBasedMatchingOperation("anyMatch",
                                                                    AnyMatchTraceInterpreter()),
                                    BreakpointBasedMatchingOperation("allMatch",
                                                                    AllMatchTraceInterpreter()),
                                    BreakpointBasedMatchingOperation("noneMatch",
                                                                    NoneMatchTraceInterpreter()),
                                    BreakpointBasedOptionalResultOperation("min"),
                                    BreakpointBasedOptionalResultOperation("max"),
                                    BreakpointBasedOptionalResultOperation("findAny"),
                                    BreakpointBasedOptionalResultOperation("findFirst"),
                                    BreakpointBasedToCollectionOperation("toArray"),
                                    BreakpointBasedToCollectionOperation("toList"),
                                    BreakpointBasedToCollectionOperation("collect"))
  }

  override val breakpointResolverFactory: BreakpointPositionResolver = JavaBreakpointPositionResolver()
}