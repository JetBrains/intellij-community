// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.KotlinIdeaReplBundle
import org.jetbrains.kotlin.cli.common.repl.replNormalizeLineBreaks
import org.jetbrains.kotlin.cli.common.repl.replUnescapeLineBreaks
import org.jetbrains.kotlin.console.actions.logError
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.utils.repl.ReplEscapeType
import org.jetbrains.kotlin.utils.repl.ReplEscapeType.*
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory

data class SeverityDetails(val severity: Severity, val description: String, val range: TextRange)

class ReplOutputHandler(
    private val runner: KotlinConsoleRunner,
    process: Process,
    commandLine: String
) : OSProcessHandler(process, commandLine) {

    private var isBuildInfoChecked = false
    private val factory = DocumentBuilderFactory.newInstance()
    private val outputProcessor = ReplOutputProcessor(runner)
    private val inputBuffer = StringBuilder()

    override fun isSilentlyDestroyOnClose() = true

    override fun notifyTextAvailable(text: String, key: Key<*>) {
        // hide warning about adding test folder to classpath
        if (text.startsWith("warning: classpath entry points to a non-existent location")) return

        if (key == ProcessOutputTypes.STDOUT) {
            inputBuffer.append(text)
            val resultingText = inputBuffer.toString()
            if (resultingText.endsWith("\n")) {
                handleReplMessage(resultingText)
                inputBuffer.setLength(0)
            }
        } else {
            super.notifyTextAvailable(text, key)
        }
    }

    private fun handleReplMessage(text: String) {
        if (text.isBlank()) return
        val output = try {
            factory.newDocumentBuilder().parse(strToSource(text))
        } catch (e: Exception) {
            logError(ReplOutputHandler::class.java, "Couldn't parse REPL output: $text", e)
            return
        }

        val root = output.firstChild as Element
        val outputType = ReplEscapeType.valueOfOrNull(root.getAttribute("type"))
        val content = root.textContent.replUnescapeLineBreaks().replNormalizeLineBreaks()

        when (outputType) {
            INITIAL_PROMPT -> buildWarningIfNeededBeforeInit(content)
            HELP_PROMPT -> outputProcessor.printHelp(content)
            USER_OUTPUT -> outputProcessor.printUserOutput(content)
            REPL_RESULT -> outputProcessor.printResultWithGutterIcon(content)
            READLINE_START -> runner.isReadLineMode = true
            READLINE_END -> runner.isReadLineMode = false
            REPL_INCOMPLETE -> outputProcessor.highlightCompilerErrors(createCompilerMessages(content))
            COMPILE_ERROR,
            RUNTIME_ERROR -> outputProcessor.printRuntimeError("${content.trim()}\n")
            INTERNAL_ERROR -> outputProcessor.printInternalErrorMessage(content)
            ERRORS_REPORTED -> {}
            SUCCESS -> runner.commandHistory.lastUnprocessedEntry()?.entryText?.let { runner.successfulLine(it) }
            else -> logError(ReplOutputHandler::class.java, "Unexpected output type:\n$outputType")
        }

        if (outputType in setOf(SUCCESS, ERRORS_REPORTED, READLINE_END)) {
            runner.commandHistory.entryProcessed()
        }
    }

    private fun buildWarningIfNeededBeforeInit(content: String) {
        if (!isBuildInfoChecked) {
            outputProcessor.printBuildInfoWarningIfNeeded()
            isBuildInfoChecked = true
        }
        // there are several INITIAL_PROMPT messages, the 1st starts with `Welcome to Kotlin version ...`
        if (content.startsWith("Welcome")) {
            outputProcessor.printUserOutput(KotlinIdeaReplBundle.message("kotlin.repl.is.experimental") + "\n")
        }
        outputProcessor.printInitialPrompt(content)
    }

    private fun strToSource(s: String, encoding: Charset = Charsets.UTF_8) = InputSource(ByteArrayInputStream(s.toByteArray(encoding)))

    private fun createCompilerMessages(runtimeErrorsReport: String): List<SeverityDetails> {
        val compilerMessages = arrayListOf<SeverityDetails>()

        val report = factory.newDocumentBuilder().parse(strToSource(runtimeErrorsReport, Charsets.UTF_16))
        val entries = report.getElementsByTagName("reportEntry")
        for (i in 0 until entries.length) {
            val reportEntry = entries.item(i) as Element

            val severityLevel = reportEntry.getAttribute("severity").toSeverity()
            val rangeStart = reportEntry.getAttribute("rangeStart").toInt()
            val rangeEnd = reportEntry.getAttribute("rangeEnd").toInt()
            val description = reportEntry.textContent

            compilerMessages.add(SeverityDetails(severityLevel, description, TextRange(rangeStart, rangeEnd)))
        }

        return compilerMessages
    }

    private fun String.toSeverity() = when (this) {
        "ERROR" -> Severity.ERROR
        "WARNING" -> Severity.WARNING
        "INFO" -> Severity.INFO
        else -> throw IllegalArgumentException("Unsupported Severity: '$this'") // this case shouldn't occur
    }
}