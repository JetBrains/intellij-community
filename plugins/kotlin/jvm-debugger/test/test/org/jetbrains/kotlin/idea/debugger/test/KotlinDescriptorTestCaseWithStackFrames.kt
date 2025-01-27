// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.JavaExecutionStack
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.intellij.xdebugger.impl.frame.XFramesView
import org.jetbrains.kotlin.idea.debugger.test.util.XDebuggerTestUtil
import org.jetbrains.kotlin.idea.debugger.test.util.iterator
import java.io.PrintWriter
import java.io.StringWriter

abstract class KotlinDescriptorTestCaseWithStackFrames : KotlinDescriptorTestCaseWithStepping() {
    private companion object {
        const val INDENT_FRAME = 1
        const val INDENT_VARIABLES = 2
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
        if (frame == null) return

        if (frame is XDebuggerFramesList.ItemWithSeparatorAbove && frame.hasSeparatorAbove()) {
            out(0, frame.captionAboveOf)
        }

        if (frame is XFramesView.HiddenStackFramesItem) {
            out(INDENT_FRAME, "<hidden frames>")
        } else {
            out(INDENT_FRAME, frame.javaClass.simpleName + " FRAME:" + XDebuggerTestUtil.getFramePresentation(frame))
            outVariables(frame)
        }
    }

    private fun printStackTrace(executionStack: JavaExecutionStack) {
        out("Thread stack trace:")
        for (frame in XDebuggerTestUtil.collectFrames(executionStack)) {
            printStackFrame(frame)
        }
    }

    fun printStackTrace() {
        doWhenXSessionPausedThenResume {
            printContext(debugProcess.debuggerContext)
            val suspendContext = debuggerSession.xDebugSession?.suspendContext as? SuspendContextImpl
            val executionStack = suspendContext?.activeExecutionStack
            if (executionStack != null) {
                try {
                    printStackTrace(executionStack)
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
}
