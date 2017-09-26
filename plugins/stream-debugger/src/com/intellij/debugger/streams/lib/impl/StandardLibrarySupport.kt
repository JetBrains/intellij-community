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

import com.intellij.debugger.streams.trace.dsl.impl.DslImpl
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctTraceHandler
import com.intellij.debugger.streams.trace.impl.interpret.AllMatchTraceInterpreter
import com.intellij.debugger.streams.trace.impl.interpret.AnyMatchTraceInterpreter
import com.intellij.debugger.streams.trace.impl.interpret.NoneMatchTraceInterpreter
import com.intellij.openapi.project.Project

/**
 * @author Vitaliy.Bibaev
 */
class StandardLibrarySupport(project: Project)
  : LibrarySupportBase(LibraryImpl("Java 8 Stream API", JavaLanguage(project), "java.util.stream")) {

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
                                    ToCollectionOperation("collect"))
  }
}