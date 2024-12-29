// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

internal class CoroutineViewDebugSessionListener(
    private val session: XDebugSession,
    private val coroutineView: CoroutineView
) : XDebugSessionListener {
    val log by logger

    override fun sessionPaused() {
        val suspendContext = session.suspendContext ?: return requestClear()
        coroutineView.alarm.cancel()
        renew(suspendContext)
    }

    override fun sessionResumed() {
        coroutineView.saveState()
        val suspendContext = session.suspendContext ?: return requestClear()
        renew(suspendContext)
    }

    override fun sessionStopped() {
        val suspendContext = session.suspendContext ?: return requestClear()
        renew(suspendContext)
    }

    override fun stackFrameChanged() {
        coroutineView.saveState()
    }

    override fun beforeSessionResume() {
    }

    override fun settingsChanged() {
        val suspendContext = session.suspendContext ?: return requestClear()
        renew(suspendContext)
    }

    private fun renew(suspendContext: XSuspendContext) {
        if (suspendContext is SuspendContextImpl) {
            DebuggerUIUtil.invokeLater {
                if (coroutineView.isShowing()) {
                    coroutineView.renewRoot(suspendContext)
                }
            }
        }
    }

    private fun requestClear() {
        if (isUnitTestMode()) { // no delay in tests
            coroutineView.resetRoot()
        } else {
            coroutineView.alarm.cancelAndRequest()
        }
    }
}
