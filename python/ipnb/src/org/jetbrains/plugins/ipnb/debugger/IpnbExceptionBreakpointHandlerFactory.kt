// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.jetbrains.python.debugger.ExceptionBreakpointHandler
import com.jetbrains.python.debugger.PyBreakpointHandlerFactory
import com.jetbrains.python.debugger.PyDebugProcess

class IpnbExceptionBreakpointHandlerFactory : PyBreakpointHandlerFactory() {
  override fun createBreakpointHandler(process: PyDebugProcess): XBreakpointHandler<*> {
    return IpnbExceptionBreakpointHandler(process)
  }
}

class IpnbExceptionBreakpointHandler(debugProcess: PyDebugProcess) :
  ExceptionBreakpointHandler<IpnbExceptionBreakpointProperties>(debugProcess, IpnbExceptionBreakpointType::class.java)