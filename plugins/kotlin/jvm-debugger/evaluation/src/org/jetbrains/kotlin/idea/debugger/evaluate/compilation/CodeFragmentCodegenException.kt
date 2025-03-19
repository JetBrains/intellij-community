// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.readText
import com.sun.jdi.InvocationException
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.LOG
import org.jetbrains.kotlin.psi.KtCodeFragment

class CodeFragmentCodegenException(val reason: Throwable) : Exception()

fun reportErrorWithAttachments(
    executionContext: ExecutionContext,
    codeFragment: KtCodeFragment,
    reason: Throwable,
    additionalAttachments: List<Pair<String, String>> = emptyList(),
    headerMessage: @NlsSafe String = "Error when compiling code fragment with IR evaluator"
) {
    val evaluationContext = executionContext.evaluationContext
    val projectName = evaluationContext.project.name
    val suspendContext = evaluationContext.suspendContext

    val file = suspendContext.activeExecutionStack?.topFrame?.sourcePosition?.file
    val fileContents = file?.readText()

    val sessionName = suspendContext.debugProcess.session.sessionName

    fun frameToLocation(it: StackFrameProxyImpl): String {
        val location = it.location()
        return "${location.method()} at line ${location.lineNumber()}"
    }

    var e2: Throwable? = null
    val messageFromTargetJvm = try {
        ((reason as? EvaluateException)?.cause as? InvocationException)?.exception()?.let {
            DebuggerUtils.tryExtractExceptionMessage(it)
        }
    } catch (e: Throwable) {
        e2 = e
        null
    }

    val message = messageFromTargetJvm?.let { "$headerMessage: Target JVM Message: $it" } ?: headerMessage

    val selectedFrame = executionContext.frameProxy
    val debuggerContext = """
            project: $projectName
            session: $sessionName
            location: ${frameToLocation(selectedFrame)}
            suspend context: ${suspendContext}
            message: $message
        """.trimIndent()

    val suspendStackTrace = suspendContext.thread?.frames()?.joinToString(System.lineSeparator()) { frame ->
        buildString {
            if (frame === selectedFrame) {
                append("(*) ")
            }
            append(frameToLocation(frame))
        }
    }

    val attachments = buildList {
        add(Attachment("debugger_context.txt", debuggerContext).apply { isIncluded = true })
        add(Attachment("code_fragment.txt", codeFragment.text).apply { isIncluded = true })
        suspendStackTrace?.let {
            add(Attachment("suspend_stack_trace.txt", it).apply { isIncluded = true })
        }
        fileContents?.let {
            add(Attachment("opened_file_contents.txt", it).apply { isIncluded = true })
        }
        for ((name, contents) in additionalAttachments) {
            add(Attachment(name, contents).apply { isIncluded = true })
        }
    }

    LOG.error(message, RuntimeExceptionWithAttachments(reason, *attachments.toTypedArray()))

    e2?.let { throw it }
}