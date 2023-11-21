// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.actions.AsyncStacksToggleAction
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendManagerUtil
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.SkipCoroutineStackFrameProxyImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.util.CoroutineFrameBuilder
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class CoroutineStackFrameInterceptor(val project: Project) : StackFrameInterceptor {
    override fun createStackFrame(frame: StackFrameProxyImpl, debugProcess: DebugProcessImpl): XStackFrame? {
        if (debugProcess.xdebugProcess?.session is XDebugSessionImpl
            && frame !is SkipCoroutineStackFrameProxyImpl
            && AsyncStacksToggleAction.isAsyncStacksEnabled(debugProcess.xdebugProcess?.session as XDebugSessionImpl)) {
            val suspendContextImpl = SuspendManagerUtil.getContextForEvaluation(debugProcess.suspendManager)
            val stackFrame = suspendContextImpl?.let {
                CoroutineFrameBuilder.coroutineExitFrame(frame, it)
            }

            if (stackFrame != null) {
                showCoroutinePanel(debugProcess)
            }

            return stackFrame
        }
        return null
    }

    private fun showCoroutinePanel(debugProcess: DebugProcessImpl) {
        val ui = debugProcess.session.xDebugSession?.ui.safeAs<RunnerLayoutUiImpl>() ?: return
        val runnerContentUi = RunnerContentUi.KEY.getData(ui) ?: return
        runInEdt {
            runnerContentUi.findOrRestoreContentIfNeeded(CoroutineDebuggerContentInfo.XCOROUTINE_THREADS_CONTENT)
        }
    }
}
