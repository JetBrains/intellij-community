// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.resolve.*
import com.intellij.debugger.streams.trace.IntermediateCallHandler
import com.intellij.debugger.streams.trace.dsl.Dsl
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
                              { num, call, dsl -> PeekTraceHandler(num, call.name, call.typeBefore, call.typeAfter, dsl) },
                              SimplePeekCallTraceInterpreter(),
                              orderResolver)

class FilterOperation(name: String) : OrderBasedOperation(name, FilterResolver())
class MappingOperation(name: String) : OrderBasedOperation(name, MapResolver())
class FlatMappingOperation(name: String) : OrderBasedOperation(name, FlatMapResolver())
class SortedOperation(name: String) : OrderBasedOperation(name, IdentityResolver())

class DistinctOperation(name: String, handlerFactory: (Int, IntermediateStreamCall, Dsl) -> IntermediateCallHandler)
  : IntermediateOperationBase(name, handlerFactory, DistinctCallTraceInterpreter(), DistinctResolver())

class ParallelOperation(name: String) : IntermediateOperationBase(name,
                                                                            { num, call, dsl -> ParallelHandler(num, call, dsl) },
                                                                            SimplePeekCallTraceInterpreter(), FilterResolver())

class ConcatOperation(name: String, orderResolver: ValuesOrderResolver) : OrderBasedOperation(name, orderResolver)
class CollapseOperation(name: String) : OrderBasedOperation(name, CollapseResolver())