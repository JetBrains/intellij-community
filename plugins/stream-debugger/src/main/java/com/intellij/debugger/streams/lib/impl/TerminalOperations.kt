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

import com.intellij.debugger.streams.resolve.AllToResultResolver
import com.intellij.debugger.streams.resolve.IdentityResolver
import com.intellij.debugger.streams.resolve.OptionalOrderResolver
import com.intellij.debugger.streams.trace.CallTraceInterpreter
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.impl.handler.unified.MatchHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.OptionalTerminationHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.TerminatorTraceHandler
import com.intellij.debugger.streams.trace.impl.interpret.CollectIdentityTraceInterpreter
import com.intellij.debugger.streams.trace.impl.interpret.OptionalTraceInterpreter

/**
 * @author Vitaliy.Bibaev
 */

class MatchingOperation(name: String, interpreter: CallTraceInterpreter, dsl: Dsl)
  : TerminalOperationBase(name, { call, _ -> MatchHandler(call, dsl) }, interpreter, AllToResultResolver())

class OptionalResultOperation(name: String, dsl: Dsl)
  : TerminalOperationBase(name, { call, expr -> OptionalTerminationHandler(call, expr, dsl) },
                          OptionalTraceInterpreter(), OptionalOrderResolver())

class ToCollectionOperation(name: String, dsl: Dsl)
  : TerminalOperationBase(name, { call, _ -> TerminatorTraceHandler(call, dsl) },
                          CollectIdentityTraceInterpreter(), IdentityResolver())