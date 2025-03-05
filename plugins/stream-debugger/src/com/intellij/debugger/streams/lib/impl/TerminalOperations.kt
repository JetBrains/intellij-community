// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.impl.TerminalOperationBase
import com.intellij.debugger.streams.core.resolve.AllToResultResolver
import com.intellij.debugger.streams.core.resolve.OptionalOrderResolver
import com.intellij.debugger.streams.core.trace.CallTraceInterpreter
import com.intellij.debugger.streams.core.trace.impl.interpret.OptionalTraceInterpreter
import com.intellij.debugger.streams.trace.impl.handler.unified.MatchHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.OptionalTerminationHandler

/**
 * @author Vitaliy.Bibaev
 */

class MatchingOperation(name: String, interpreter: CallTraceInterpreter)
  : TerminalOperationBase(name, { call, _, dsl -> MatchHandler(call, dsl) }, interpreter, AllToResultResolver())

class OptionalResultOperation(name: String)
  : TerminalOperationBase(name, { call, expr, dsl -> OptionalTerminationHandler(call, expr, dsl) },
                          OptionalTraceInterpreter(), OptionalOrderResolver())