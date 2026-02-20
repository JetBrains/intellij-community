// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.impl.DistinctOperation
import com.intellij.debugger.streams.core.lib.impl.FilterOperation
import com.intellij.debugger.streams.core.lib.impl.FlatMappingOperation
import com.intellij.debugger.streams.core.lib.impl.MappingOperation
import com.intellij.debugger.streams.core.lib.impl.SortedOperation
import com.intellij.debugger.streams.core.lib.impl.ToCollectionOperation
import com.intellij.debugger.streams.core.trace.impl.handler.unified.DistinctTraceHandler
import com.intellij.debugger.streams.core.trace.impl.interpret.AllMatchTraceInterpreter
import com.intellij.debugger.streams.core.trace.impl.interpret.AnyMatchTraceInterpreter
import com.intellij.debugger.streams.core.trace.impl.interpret.NoneMatchTraceInterpreter
import com.intellij.debugger.streams.trace.breakpoint.BreakpointPositionResolver
import com.intellij.debugger.streams.trace.breakpoint.JavaBreakpointPositionResolver
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedFilterOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedMappingOperation
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedToCollectionOperation

/**
 * @author Vitaliy.Bibaev
 */
class StandardLibrarySupport : JvmLibrarySupportBase() {
  init {
    addIntermediateOperationsSupport(BreakpointBasedFilterOperation("filter"),
                                     FilterOperation("limit"),
                                     FilterOperation("skip"),
                                     FilterOperation("peek"),
                                     FilterOperation("onClose"),
                                     BreakpointBasedMappingOperation("map"),
                                     MappingOperation("mapToInt"),
                                     MappingOperation("mapToLong"),
                                     MappingOperation("mapToDouble"),
                                     MappingOperation("mapToObj"),
                                     MappingOperation("boxed"),
                                     FlatMappingOperation("flatMap"),
                                     FlatMappingOperation("flatMapToInt"),
                                     FlatMappingOperation("flatMapToLong"),
                                     FlatMappingOperation("flatMapToDouble"),
                                     DistinctOperation("distinct", { num, call, dsl -> DistinctTraceHandler(num, call, dsl) }),
                                     SortedOperation("sorted"),
                                     ParallelOperation("parallel"))

    addTerminationOperationsSupport(MatchingOperation("anyMatch",
                                                      AnyMatchTraceInterpreter()),
                                    MatchingOperation("allMatch",
                                                      AllMatchTraceInterpreter()),
                                    MatchingOperation("noneMatch",
                                                      NoneMatchTraceInterpreter()),
                                    OptionalResultOperation("min"),
                                    OptionalResultOperation("max"),
                                    OptionalResultOperation("findAny"),
                                    OptionalResultOperation("findFirst"),
                                    ToCollectionOperation("toArray"),
                                    BreakpointBasedToCollectionOperation("toList"),
                                    ToCollectionOperation("collect"))
  }

  override val breakpointResolverFactory: BreakpointPositionResolver = JavaBreakpointPositionResolver()
}