// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.inlay

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManagerListener

internal class StreamDebuggerInlaySessionListener(@Suppress("unused") val project: Project) : XDebuggerManagerListener {
  override fun processStarted(debugProcess: XDebugProcess) {
    if (debugProcess is JavaDebugProcess) {
      debugProcess.session.addSessionListener(StreamDebuggerInlayDisplay(debugProcess.session))
    }
  }
}