// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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