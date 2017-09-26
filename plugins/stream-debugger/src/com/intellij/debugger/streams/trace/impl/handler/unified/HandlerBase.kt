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
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.trace.IntermediateCallHandler
import com.intellij.debugger.streams.trace.TerminatorCallHandler
import com.intellij.debugger.streams.trace.TraceHandler
import com.intellij.debugger.streams.trace.dsl.Dsl

/**
 * @author Vitaliy.Bibaev
 */
abstract class HandlerBase private constructor(protected val dsl: Dsl) : TraceHandler {
  abstract class Intermediate(dsl: Dsl) : HandlerBase(dsl), IntermediateCallHandler

  abstract class Terminal(dsl: Dsl) : HandlerBase(dsl), TerminatorCallHandler
}