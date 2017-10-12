// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.resolve.AllToResultResolver
import com.intellij.debugger.streams.resolve.IdentityResolver
import com.intellij.debugger.streams.resolve.OptionalOrderResolver
import com.intellij.debugger.streams.trace.CallTraceInterpreter
import com.intellij.debugger.streams.trace.impl.handler.unified.ToCollectionHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.MatchHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.OptionalTerminationHandler
import com.intellij.debugger.streams.trace.impl.interpret.CollectIdentityTraceInterpreter
import com.intellij.debugger.streams.trace.impl.interpret.OptionalTraceInterpreter

/**
 * @author Vitaliy.Bibaev
 */

class MatchingOperation(name: String, interpreter: CallTraceInterpreter)
  : TerminalOperationBase(name, { call, _, dsl -> MatchHandler(call, dsl) }, interpreter, AllToResultResolver())

class OptionalResultOperation(name: String)
  : TerminalOperationBase(name, { call, expr, dsl -> OptionalTerminationHandler(call, expr, dsl) },
                          OptionalTraceInterpreter(), OptionalOrderResolver())

class ToCollectionOperation(name: String)
  : TerminalOperationBase(name, { call, _, dsl -> ToCollectionHandler(call, dsl) },
                          CollectIdentityTraceInterpreter(), IdentityResolver())