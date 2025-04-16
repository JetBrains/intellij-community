// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise


class CodeAnalysisCommand(text: String, line: Int) : AbstractCommand(text, line) {

  companion object {
    const val PREFIX = CMD_PREFIX + "codeAnalysis"

    private val LOG = logger<CodeAnalysisCommand>()
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val project = context.project
    val commandArgs = extractCommandArgument(PREFIX).split(" ", limit = 2)
    val commandType = commandArgs[0]
    DumbService.getInstance(project).waitForSmartMode()
    val busConnection = project.messageBus.connect()


    busConnection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      @Suppress("TestOnlyProblems")
      override fun daemonFinished() {
        val myDaemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        require(editor != null)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        require(psiFile != null)
        if (myDaemonCodeAnalyzer.isErrorAnalyzingFinished(psiFile)) {
          busConnection.disconnect()
          when (commandType) {
            "CHECK_ON_RED_CODE" -> {
              val errorsOnHighlighting =
                DaemonCodeAnalyzerImpl.getHighlights(editor.document, HighlightSeverity.ERROR, project)
              if (errorsOnHighlighting.size > 0) {
                val errorMessages = StringBuilder("Analysis on red code detected some errors: " + errorsOnHighlighting.size)
                errorsOnHighlighting.forEach {
                  errorMessages.append("\n").append(it.description)
                }
                actionCallback.reject(errorMessages.toString())
              }
              else {
                context.message("Analysis on red code performed successfully", line)
                actionCallback.setDone()
              }
            }
            "WARNINGS_ANALYSIS" -> {
              val warningsOnHighlighting = DaemonCodeAnalyzerImpl.getHighlights(editor.document, HighlightSeverity.WARNING, project)
              if (warningsOnHighlighting.size > 0) {
                val warnings = StringBuilder("Highlighting detected some warnings: " + warningsOnHighlighting.size)
                for (warning in warningsOnHighlighting) {
                  warnings.append("\n").append(warning.description)
                }
                LOG.info(warnings.toString())
                val args = when (commandArgs.size > 1) {
                  true -> commandArgs[1].split(",")
                  false -> listOf()
                }
                if (args.isNotEmpty()) {
                  args.forEach {
                    var found = false
                    for (warning in warningsOnHighlighting) {
                      if (warning.description.contains(it)) {
                        found = true
                        break
                      }
                    }
                    if (!found) {
                      actionCallback.reject("Highlighting did not detect the warning $it")
                    }
                  }
                }
                if (!actionCallback.isRejected) {
                  actionCallback.setDone()
                }
              }
              else {
                actionCallback.reject("Highlighting did not detect any warning")
              }
            }
            else -> error("Wrong type of code analysis: $commandType")
          }
        }
      }
    })

    DumbService.getInstance(project).smartInvokeLater {
      PsiManager.getInstance(project).dropPsiCaches()
      context.message("Code highlighting started", line)
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
    return actionCallback.toPromise()
  }
}

