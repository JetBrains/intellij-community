// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.lib

import com.intellij.debugger.streams.core.trace.IntermediateCallHandler
import com.intellij.debugger.streams.core.trace.TerminatorCallHandler
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import org.jetbrains.annotations.NonNls

/**
 * @author Vitaliy.Bibaev
 */
interface HandlerFactory {
  fun getForIntermediate(number: Int, call: IntermediateStreamCall): IntermediateCallHandler
  fun getForTermination(call: TerminatorStreamCall, @NonNls resultExpression: String): TerminatorCallHandler
}