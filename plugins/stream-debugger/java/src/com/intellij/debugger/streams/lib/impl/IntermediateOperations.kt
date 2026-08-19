// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.impl.IntermediateOperationBase
import com.intellij.debugger.streams.core.resolve.FilterResolver
import com.intellij.debugger.streams.core.resolve.NopResolver
import com.intellij.debugger.streams.trace.impl.handler.unified.GatherTraceHandler
import com.intellij.debugger.streams.core.trace.impl.interpret.SimplePeekCallTraceInterpreter
import com.intellij.debugger.streams.trace.impl.handler.unified.ParallelHandler
import com.intellij.debugger.streams.trace.impl.interpret.GatherCallTraceInterpreter

open class ParallelOperation(name: String) : IntermediateOperationBase(name,
                                                                  { num, call, dsl -> ParallelHandler(num, call, dsl) },
                                                                  SimplePeekCallTraceInterpreter(), FilterResolver())

open class GatherOperation(name: String) : IntermediateOperationBase(name,
                              { num, call, dsl -> GatherTraceHandler(num, call, dsl) },
                              GatherCallTraceInterpreter(),
                              NopResolver)