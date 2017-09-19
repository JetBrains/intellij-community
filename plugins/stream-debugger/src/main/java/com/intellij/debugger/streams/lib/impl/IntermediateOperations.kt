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
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.impl.DslImpl
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory
import com.intellij.debugger.streams.trace.impl.handler.unified.ParallelHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.PeekTraceHandler
import com.intellij.debugger.streams.trace.impl.interpret.DistinctCallTraceInterpreter
import com.intellij.debugger.streams.trace.impl.interpret.SimplePeekCallTraceInterpreter
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */

open class OrderBasedOperation(name: String, orderResolver: ValuesOrderResolver)
  : IntermediateOperationBase(name,
                              { num, call -> PeekTraceHandler(num, call.name, call.typeBefore, call.typeAfter, JAVA_DSL) },
                              SimplePeekCallTraceInterpreter(),
                              orderResolver) {
  private companion object {
    val JAVA_DSL = DslImpl(JavaStatementFactory())
  }
}

class FilterOperation(name: String) : OrderBasedOperation(name, FilterResolver())
class MappingOperation(name: String) : OrderBasedOperation(name, MapResolver())
class FlatMappingOperation(name: String) : OrderBasedOperation(name, FlatMapResolver())
class SortedOperation(name: String) : OrderBasedOperation(name, IdentityResolver())

class DistinctOperation(name: String, handlerFactory: (Int, IntermediateStreamCall) -> IntermediateCallHandler)
  : IntermediateOperationBase(name, handlerFactory, DistinctCallTraceInterpreter(), DistinctResolver())

class ParallelOperation(name: String, dsl: Dsl) : IntermediateOperationBase(name,
                                                                            {num, call -> ParallelHandler(num, call, dsl) },
                                                                            SimplePeekCallTraceInterpreter(), FilterResolver())

class ConcatOperation(name: String, orderResolver: ValuesOrderResolver) : OrderBasedOperation(name, orderResolver)
class CollapseOperation(name: String) : OrderBasedOperation(name, CollapseResolver())