// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.AsyncStackTraceProvider
import com.intellij.debugger.engine.JavaExecutionStack
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.Semaphore
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import org.jetbrains.kotlin.idea.debugger.coroutine.CoroutineAsyncStackTraceProvider
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutinePreflightFrame
import org.jetbrains.kotlin.idea.debugger.test.util.XDebuggerTestUtil
import org.jetbrains.kotlin.idea.debugger.test.util.iterator
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.io.PrintWriter
import java.io.StringWriter

abstract class KotlinDescriptorTestCaseWithStackFrames : KotlinDescriptorTestCaseWithStepping() {
    private companion object {
        val ASYNC_STACKTRACE_EP_NAME = AsyncStackTraceProvider.EP.name
        const val INDENT_FRAME = 1
        const val INDENT_VARIABLES = 2
    }

    private fun out(frame: XStackFrame) {
        out(INDENT_FRAME, frame.javaClass.simpleName + " FRAME:" + XDebuggerTestUtil.getFramePresentation(frame))
        outVariables(frame)
    }

    private fun outVariables(stackFrame: XStackFrame) {
        val variables = stackFrame.iterator().asSequence()
            .filterIsInstance<XNamedValue>()
            .map { it.name }
            .sorted()
            .toList()
        out(INDENT_VARIABLES, "(${variables.joinToString()})")
    }

    private fun out(text: String) {
        println(text, ProcessOutputTypes.SYSTEM)
    }

    private fun out(indent: Int, text: String) {
        println("\t".repeat(indent) + text, ProcessOutputTypes.SYSTEM)
    }

    private fun Throwable.stackTraceAsString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun printStackFrame(frame: XStackFrame?) {
        if (frame == null) {
            return
        } else if (frame is XDebuggerFramesList.ItemWithSeparatorAbove &&
                   frame.hasSeparatorAbove()) {
            out(0, frame.captionAboveOf)
        }

        out(frame)
    }

    private fun printStackFrameItems(stackFrames: List<StackFrameItem>) {
        for (frameItem in stackFrames) {
            printStackFrame(frameItem.createFrame(debugProcess))
        }
    }

    private fun AsyncStackTraceProvider.getAsyncStackTraceInSuspendContextCommand(suspendContext: SuspendContextImpl, frame: JavaStackFrame): List<StackFrameItem>? {
        val semaphore = Semaphore(1)
        var stackFrames: List<StackFrameItem>? = null
        suspendContext.debugProcess.managerThread.schedule(object : SuspendContextCommandImpl(suspendContext) {
            override fun contextAction(suspendContext: SuspendContextImpl) {
                stackFrames = getAsyncStackTrace(frame, suspendContext)
                semaphore.up()
            }

            override fun commandCancelled() = semaphore.up()
        })
        semaphore.waitFor(com.intellij.xdebugger.XDebuggerTestUtil.TIMEOUT_MS.toLong())
        return stackFrames
    }

    private fun printStackTrace(asyncStackTraceProvider: AsyncStackTraceProvider?, suspendContext: SuspendContextImpl, executionStack: JavaExecutionStack) {
        out("Thread stack trace:")
        for (frame in XDebuggerTestUtil.collectFrames(executionStack)) {
            if (frame !is JavaStackFrame) {
                continue
            }

            out(frame)
            if (frame is CoroutinePreflightFrame) {
                val key = frame.coroutineInfoData.descriptor
                out(0, "CoroutineInfo: ${key.id} ${key.name} ${key.state}")
            }

            val stackFrameItems = asyncStackTraceProvider?.getAsyncStackTraceInSuspendContextCommand(suspendContext, frame)
            if (stackFrameItems != null) {
                printStackFrameItems(stackFrameItems)
                return
            }
        }
    }

    fun printStackTrace() {
        val asyncStackTraceProvider = getAsyncStackTraceProvider()
        doWhenXSessionPausedThenResume {
            printContext(debugProcess.debuggerContext)
            val suspendContext = debuggerSession.xDebugSession?.suspendContext as? SuspendContextImpl
            val executionStack = suspendContext?.activeExecutionStack
            if (suspendContext != null && executionStack != null) {
                try {
                    printStackTrace(asyncStackTraceProvider, suspendContext, executionStack)
                } catch (e: Throwable) {
                    val stackTrace = e.stackTraceAsString()
                    System.err.println("Exception occurred on calculating async stack traces: $stackTrace")
                    throw e
                }
            } else {
                println("FrameProxy is 'null', can't calculate async stack trace", ProcessOutputTypes.SYSTEM)
            }
        }
    }

    private fun getAsyncStackTraceProvider(): CoroutineAsyncStackTraceProvider? {
        val area = ApplicationManager.getApplication().extensionArea
        if (!area.hasExtensionPoint(ASYNC_STACKTRACE_EP_NAME)) {
            System.err.println("${ASYNC_STACKTRACE_EP_NAME} extension point is not found (probably old IDE version)")
            return null
        }

        val extensionPoint = area.getExtensionPoint<Any>(ASYNC_STACKTRACE_EP_NAME)
        val provider = extensionPoint.extensions.firstIsInstanceOrNull<CoroutineAsyncStackTraceProvider>()

        if (provider == null) {
            System.err.println("Kotlin coroutine async stack trace provider is not found")
        }
        return provider
    }
}
