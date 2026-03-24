// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.DapMode
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.models.DebuggerSplitTabUtils

private class DisableCoroutineViewListener : DebuggerManagerListener {
    override fun sessionAttached(session: DebuggerSession) {
        if (DapMode.isDap()) return
        session.xDebugSession?.addSessionListener(object : XDebugSessionListener {
            private var isFirstTimePaused = true

            override fun sessionPaused() {
                // without waiting pause, there are some strange unexpected changes in the debugger layout
                if (isFirstTimePaused && !Registry.`is`("debugger.kotlin.auto.show.coroutines.view")) {
                    showOrHideCoroutinePanel(session.process, false)
                }
                isFirstTimePaused = false
            }
        })
    }
}


internal fun showOrHideCoroutinePanel(debugProcess: DebugProcessImpl, needShow: Boolean) {
    if (DapMode.isDap()) return
    val xDebugSession = debugProcess.session.xDebugSession as? XDebugSessionImpl ?: return
    xDebugSession.runWhenUiReady { ui ->
        val key = CoroutineDebuggerContentInfo.XCOROUTINE_THREADS_CONTENT
        if (needShow) {
            DebuggerSplitTabUtils.restoreContent(ui, key)
        } else {
            DebuggerSplitTabUtils.hideContent(ui, key)
        }
    }
}
