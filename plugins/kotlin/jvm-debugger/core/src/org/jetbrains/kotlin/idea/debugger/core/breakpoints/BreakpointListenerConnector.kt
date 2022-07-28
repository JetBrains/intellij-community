// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener

object BreakpointListenerConnector {
    @JvmStatic
    fun subscribe(debugProcess: DebugProcessImpl, indicator: ProgressWindow, listener: XBreakpointListener<XBreakpoint<*>>) {
        debugProcess.project.messageBus.connect(indicator).subscribe(XBreakpointListener.TOPIC, listener)
    }
}
