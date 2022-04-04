// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.new_arch.lib

import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall

/**
 * @author Shumaf Lovpache
 */
interface RuntimeHandlerFactory {
  fun getForSource(): RuntimeSourceCallHandler
  fun getForIntermediate(call: IntermediateStreamCall): RuntimeIntermediateCallHandler
  fun getForTermination(call: TerminatorStreamCall): RuntimeTerminalCallHandler
}
