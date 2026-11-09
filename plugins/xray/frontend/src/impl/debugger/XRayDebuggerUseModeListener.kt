// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.debugger

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xray.frontend.impl.service.XRayModeService

/**
 * The special listener that keeps track of the debugger and notifies the X-Ray service if the debug status was changed.
 */
internal class XRayDebuggerUseModeListener(private val project: Project) : XDebuggerManagerListener {

  /**
   * Updates the editor mode for the X-Ray service (`true` if debug is active, `false` otherwise).
   */
  override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
    val service = XRayModeService.getInstance()
    service.setDebuggingUseMode(project, currentSession != null)
  }
}
