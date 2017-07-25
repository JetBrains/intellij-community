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

import com.intellij.debugger.streams.resolve.*
import com.intellij.debugger.streams.trace.IntermediateCallHandler
import com.intellij.debugger.streams.trace.impl.handler.ParallelHandler
import com.intellij.debugger.streams.trace.impl.handler.PeekTracerHandler
import com.intellij.debugger.streams.trace.impl.resolve.DistinctCallTraceResolver
import com.intellij.debugger.streams.trace.impl.resolve.SimplePeekCallTraceResolver
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */

open class OrderBasedOperation(name: String, orderResolver: ValuesOrderResolver)
  : IntermediateOperationBase(name,
                              { num, call -> PeekTracerHandler(num, call.name, call.typeBefore, call.typeAfter) },
                              SimplePeekCallTraceResolver(),
                              orderResolver)

class FilterOperation(name: String) : OrderBasedOperation(name, FilterResolver())
class MappingOperation(name: String) : OrderBasedOperation(name, MapResolver())
class FlatMappingOperation(name: String) : OrderBasedOperation(name, FlatMapResolver())
class SortedOperation(name: String) : OrderBasedOperation(name, IdentityResolver())

class DistinctOperation(name: String, handlerFactory: (Int, IntermediateStreamCall) -> IntermediateCallHandler)
  : IntermediateOperationBase(name, handlerFactory, DistinctCallTraceResolver(), DistinctResolver())

class ParallelOperation(name: String) : IntermediateOperationBase(name,
                                                                  ::ParallelHandler,
                                                                  SimplePeekCallTraceResolver(), FilterResolver())

class ConcatOperation(name: String, orderResolver: ValuesOrderResolver) : OrderBasedOperation(name, orderResolver)
class CollapseOperation(name: String) : OrderBasedOperation(name, CollapseResolver())