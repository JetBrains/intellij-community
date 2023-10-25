// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.execution.process.ProcessOutputTypes
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.util.CoroutineFrameBuilder
import org.jetbrains.kotlin.idea.debugger.coroutine.util.getSuspendExitMode
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import java.io.PrintWriter
import java.io.StringWriter

abstract class AbstractAsyncStackTraceTest : KotlinDescriptorTestCaseWithStepping() {
    private companion object {
        const val MARGIN = "    "
    }

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        doOnBreakpoint {
            val frameProxy = this.frameProxy
            if (frameProxy != null) {
                try {
                    printAsyncStackTrace(frameProxy)
                } catch (e: Throwable) {
                    val stackTrace = e.stackTraceAsString()
                    System.err.println("Exception occurred on calculating async stack traces: $stackTrace")
                    throw e
                }
            } else {
                println("FrameProxy is 'null', can't calculate async stack trace", ProcessOutputTypes.SYSTEM)
            }

            resume(this)
        }
    }

    private fun SuspendContextImpl.printAsyncStackTrace(frameProxy: StackFrameProxyImpl) {
        val sem = frameProxy.location().getSuspendExitMode()
        val coroutineInfoData =
            if (sem.isCoroutineFound())
                CoroutineFrameBuilder.lookupContinuation(this, frameProxy, sem)?.coroutineInfoData
            else
                null
        if (coroutineInfoData != null && coroutineInfoData.stackTrace.isNotEmpty()) {
            print(renderAsyncStackTrace(coroutineInfoData.stackTrace), ProcessOutputTypes.SYSTEM)
        } else {
            println("No async stack trace available", ProcessOutputTypes.SYSTEM)
        }
    }

    private fun Throwable.stackTraceAsString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun renderAsyncStackTrace(trace: List<CoroutineStackFrameItem>) = buildString {
        appendLine("Async stack trace:")
        for (item in trace) {
            append(MARGIN).appendLine(item.toString())
            for (variable in item.spilledVariables) {
                val descriptor = variable.descriptor
                val name = descriptor.calcValueName()
                val value = descriptor.calcValue(evaluationContext)

                append(MARGIN).append(MARGIN).append(name).append(" = ").appendLine(value)
            }
        }
    }
}

abstract class AbstractK1IdeK2CodeAsyncStackTraceTest : AbstractAsyncStackTraceTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}
