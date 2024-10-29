// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.XDebugSessionListener

private class DisableCoroutineViewListener : DebuggerManagerListener {
    override fun sessionAttached(session: DebuggerSession) {
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
    val ui = debugProcess.session.xDebugSession?.ui as? RunnerLayoutUiImpl ?: return
    val contentUi = ui.contentUI
    runInEdt {
        val key = CoroutineDebuggerContentInfo.XCOROUTINE_THREADS_CONTENT
        if (needShow) {
            contentUi.findOrRestoreContentIfNeeded(key)
        }
        else {
            contentUi.hideContent(key)
        }
    }
}
