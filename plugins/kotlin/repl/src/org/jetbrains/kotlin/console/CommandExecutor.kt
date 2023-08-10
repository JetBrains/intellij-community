// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console

import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.kotlin.cli.common.repl.replInputAsXml
import org.jetbrains.kotlin.console.actions.logError

class CommandExecutor(private val runner: KotlinConsoleRunner) {
    private val commandHistory = runner.commandHistory
    private val historyUpdater = HistoryUpdater(runner)

    fun executeCommand() = WriteCommandAction.runWriteCommandAction(runner.project) {
        val commandText = getTrimmedCommandText()

        if (commandText.isEmpty()) {
            return@runWriteCommandAction
        }

        val historyDocumentRange = historyUpdater.printNewCommandInHistory(commandText)
        commandHistory.addEntry(CommandHistory.Entry(commandText, historyDocumentRange))
        sendCommandToProcess(commandText)
    }

    private fun getTrimmedCommandText(): String {
        val consoleView = runner.consoleView
        val document = consoleView.editorDocument
        return document.text.trim()
    }

    private fun sendCommandToProcess(command: String) {
        val processHandler = runner.processHandler
        val processInputOS = processHandler.processInput ?: return logError(this::class.java, "<p>Broken process stream</p>")
        val charset = (processHandler as? BaseOSProcessHandler)?.charset ?: Charsets.UTF_8

        val xmlRes = command.replInputAsXml()

        val bytes = ("$xmlRes\n").toByteArray(charset)
        processInputOS.write(bytes)
        processInputOS.flush()
    }
}