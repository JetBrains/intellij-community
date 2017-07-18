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
import com.intellij.debugger.streams.resolve.OptionalOrderResolver
import com.intellij.debugger.streams.trace.CallTraceResolver
import com.intellij.debugger.streams.trace.impl.handler.MatchHandler
import com.intellij.debugger.streams.trace.impl.handler.OptionalTerminatorHandler
import com.intellij.debugger.streams.trace.impl.resolve.OptionalResolver

/**
 * @author Vitaliy.Bibaev
 */

class MatchingOperation(name: String, interpreter: CallTraceResolver)
  : TerminalOperationBase(name, { call, _ -> MatchHandler(call) }, interpreter, AllToResultResolver())

class OptionalResultOperation(name: String)
  : TerminalOperationBase(name, { call, expr -> OptionalTerminatorHandler(call, expr) }, OptionalResolver(), OptionalOrderResolver())