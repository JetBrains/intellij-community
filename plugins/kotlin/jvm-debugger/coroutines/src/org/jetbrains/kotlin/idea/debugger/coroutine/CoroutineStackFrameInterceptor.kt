// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.actions.AsyncStacksToggleAction
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.SuspendManagerUtil
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor
import org.jetbrains.kotlin.idea.debugger.core.stepping.ContinuationFilter
import org.jetbrains.kotlin.idea.debugger.coroutine.data.SuspendExitMode
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.SkipCoroutineStackFrameProxyImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.BaseContinuationImplLight
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugProbesImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.util.*
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

    override fun extractContinuationFilter(suspendContext: SuspendContextImpl): ContinuationFilter? {
        val defaultExecutionContext = suspendContext.executionContext() ?: return null

        if (!useContinuationObjectFilter.get(suspendContext.debugProcess, false)) {
            val coroutineId = DebugProbesImpl.instance(defaultExecutionContext)?.getCurrentThreadCoroutineId(defaultExecutionContext)
            if (coroutineId != null) {
                if (coroutineId == 0L) return null
                return ContinuationIdFilter(coroutineId)
            }
            else {
                useContinuationObjectFilter.set(suspendContext.debugProcess, true)
                //TODO: IDEA-341142 show nice notification about this
                thisLogger().warn("No ThreadLocal coroutine tracking is found")
            }

        }
        return continuationObjectFilter(suspendContext, defaultExecutionContext)
    }

    private fun continuationObjectFilter(
        suspendContext: SuspendContextImpl,
        defaultExecutionContext: DefaultExecutionContext
    ): ContinuationObjectFilter? {
        val frameProxy = suspendContext.frameProxy ?: return null
        val suspendExitMode = frameProxy.location().getSuspendExitMode()

        val continuation = extractContinuation(suspendExitMode, frameProxy) ?: return null

        val baseContinuation = extractBaseContinuation(continuation, defaultExecutionContext) ?: return null

        return ContinuationObjectFilter(baseContinuation)
    }

    private fun extractContinuation(mode: SuspendExitMode, frameProxy: StackFrameProxyImpl): ObjectReference? = when (mode) {
        SuspendExitMode.SUSPEND_LAMBDA -> frameProxy.thisVariableValue()
        SuspendExitMode.SUSPEND_METHOD_PARAMETER -> frameProxy.completionVariableValue() ?: frameProxy.continuationVariableValue()
        else -> null
    }

    private fun extractBaseContinuation(
        continuation: ObjectReference,
        defaultExecutionContext: DefaultExecutionContext
    ): ObjectReference? {
        val baseContinuationImpl = BaseContinuationImplLight(defaultExecutionContext)
        var loopContinuation = continuation
        while (true) {
            val continuationMirror = baseContinuationImpl.mirror(loopContinuation, defaultExecutionContext) ?: return null
            val nextContinuation = continuationMirror.nextContinuation
            if (nextContinuation == null) {
                return continuationMirror.coroutineOwner
            }
            loopContinuation = nextContinuation
        }
    }

    private data class ContinuationIdFilter(val id: Long) : ContinuationFilter

    // Is used when there is no debug information about unique Continuation ID (for example, for the old versions)
    private data class ContinuationObjectFilter(val reference: ObjectReference) : ContinuationFilter

    companion object {
        @JvmStatic
        private val useContinuationObjectFilter = Key.create<Boolean>("useContinuationObjectFilter")
    }
}
