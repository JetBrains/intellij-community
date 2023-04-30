// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.new_arch.lib

import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall

/**
 * @author Shumaf Lovpache
 */
interface RuntimeHandlerFactory {
  /**
   * Source call handler doesn't accept any information about the source call, because in general
   * we can't determine source call (for ex. we can get stream as return value from user code)
   */
  fun getForSource(): RuntimeSourceCallHandler
  fun getForIntermediate(call: IntermediateStreamCall): RuntimeIntermediateCallHandler
  fun getForTermination(call: TerminatorStreamCall): RuntimeTerminalCallHandler
}
