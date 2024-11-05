// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.resolve.FilterResolver
import com.intellij.debugger.streams.trace.impl.handler.unified.ParallelHandler
import com.intellij.debugger.streams.trace.impl.interpret.SimplePeekCallTraceInterpreter

class ParallelOperation(name: String) : IntermediateOperationBase(name,
                                                                  { num, call, dsl -> ParallelHandler(num, call, dsl) },
                                                                  SimplePeekCallTraceInterpreter(), FilterResolver())