// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.impl.*
import com.intellij.debugger.streams.core.trace.impl.handler.unified.DistinctTraceHandler
import com.intellij.debugger.streams.core.trace.impl.interpret.AllMatchTraceInterpreter
import com.intellij.debugger.streams.core.trace.impl.interpret.AnyMatchTraceInterpreter
import com.intellij.debugger.streams.core.trace.impl.interpret.NoneMatchTraceInterpreter

/**
 * @author Vitaliy.Bibaev
 */
class StandardLibrarySupport
  : LibrarySupportBase() {

  init {
    addIntermediateOperationsSupport(FilterOperation("filter"),
                                     FilterOperation("limit"),
                                     FilterOperation("skip"),
                                     FilterOperation("peek"),
                                     FilterOperation("onClose"),
                                     MappingOperation("map"),
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
                                    ToCollectionOperation("toList"),
                                    ToCollectionOperation("collect"))
  }
}