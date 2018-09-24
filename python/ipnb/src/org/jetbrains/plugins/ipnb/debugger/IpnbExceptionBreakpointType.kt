// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.icons.AllIcons
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import javax.swing.Icon


class IpnbExceptionBreakpointType : XBreakpointType<XBreakpoint<IpnbExceptionBreakpointProperties>, IpnbExceptionBreakpointProperties>(
  "jupyter-exception", "Jupyter Exception Breakpoint") {

  override fun getEnabledIcon(): Icon {
    return AllIcons.Debugger.Db_exception_breakpoint
  }

  override fun getDisabledIcon(): Icon {
    return AllIcons.Debugger.Db_disabled_exception_breakpoint
  }

  override fun createProperties(): IpnbExceptionBreakpointProperties? {
    return IpnbExceptionBreakpointProperties(BASE_EXCEPTION)
  }

  override fun getBreakpointsDialogHelpTopic(): String? {
    return "reference.dialogs.breakpoints"
  }


  override fun getDisplayText(breakpoint: XBreakpoint<IpnbExceptionBreakpointProperties>): String {
    val properties = breakpoint.properties
    if (properties != null) {
      val exception = properties.exception
      if (BASE_EXCEPTION == exception) {
        return "Any exception"
      }
      return exception
    }
    return ""
  }

  override fun createDefaultBreakpoint(creator: XBreakpointType.XBreakpointCreator<IpnbExceptionBreakpointProperties>): XBreakpoint<IpnbExceptionBreakpointProperties>? {
    val breakpoint = creator.createBreakpoint(createDefaultBreakpointProperties())
    breakpoint.isEnabled = false
    return breakpoint
  }

  companion object {

    const val BASE_EXCEPTION = "BaseException"

    private fun createDefaultBreakpointProperties(): IpnbExceptionBreakpointProperties {
      return IpnbExceptionBreakpointProperties(BASE_EXCEPTION)
    }
  }
}