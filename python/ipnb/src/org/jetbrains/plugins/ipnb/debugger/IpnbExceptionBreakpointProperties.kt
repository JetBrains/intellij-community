// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.jetbrains.python.debugger.ExceptionBreakpointProperties
import com.jetbrains.python.debugger.pydev.AbstractCommand
import com.jetbrains.python.debugger.pydev.ExceptionBreakpointCommand
import com.jetbrains.python.debugger.pydev.RemoteDebugger

class IpnbExceptionBreakpointProperties() : ExceptionBreakpointProperties<IpnbExceptionBreakpointProperties>() {

  constructor(exception: String) : this() {
    myException = exception
  }

  override fun getState(): IpnbExceptionBreakpointProperties? {
    return this
  }

  override fun loadState(state: IpnbExceptionBreakpointProperties) {
    myException = state.myException
  }

  override fun getExceptionBreakpointId(): String {
    return "jupyter-$exception"
  }

  override fun getException(): String {
    return if (myException == null) IpnbExceptionBreakpointType.BASE_EXCEPTION else myException
  }

  override fun createAddCommand(debugger: RemoteDebugger): ExceptionBreakpointCommand {
    return ExceptionBreakpointCommand(debugger, AbstractCommand.ADD_EXCEPTION_BREAKPOINT, exceptionBreakpointId)
  }

  override fun createRemoveCommand(debugger: RemoteDebugger): ExceptionBreakpointCommand {
    return ExceptionBreakpointCommand(debugger, AbstractCommand.REMOVE_EXCEPTION_BREAKPOINT, exceptionBreakpointId)
  }

}