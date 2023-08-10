package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.Ref
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.io.path.readLines


class WaitForFinishedCodeAnalysis(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "waitForFinishedCodeAnalysis"
    private val LOG = logger<WaitForFinishedCodeAnalysis>()
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val project = context.project
    val connection = project.messageBus.simpleConnect()
    val dateTimeWhenCodeAnalysisFinished = Ref<LocalDateTime>()
    LOG.info("Subscribing")
    val wasEntireFileHighlighted = Ref<Boolean>(false)
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
      @Volatile
      private var canceled: Boolean = false

      override fun daemonStarting(fileEditors: Collection<FileEditor>) {
        canceled = false
      }

      override fun daemonCancelEventOccurred(reason: String) {
        canceled = true
      }

      override fun daemonFinished(fileEditors: Collection<FileEditor>) {
        val editor = fileEditors.filterIsInstance<TextEditor>().firstOrNull() ?: return
        val entireFileHighlighted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(editor, project)

        if (!canceled && entireFileHighlighted) {
          // ensure other listeners have been executed
          ApplicationManager.getApplication().assertIsDispatchThread()
          invokeLater {
            wasEntireFileHighlighted.set(entireFileHighlighted)

            val rowFirstDateTimeFromLog = Paths.get(PathManager.getLogPath(), "idea.log")
              .readLines()
              .first()
              .substringBefore("[")
              .replace(",", ".")
              .trim()
            val dateTimeWhenAppStarted = LocalDateTime.parse(rowFirstDateTimeFromLog, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            LOG.info("Total opening time is : ${ChronoUnit.MILLIS.between(dateTimeWhenAppStarted, LocalDateTime.now())}")

            actionCallback.setDone()
          }
        }
      }
    })

    return actionCallback.toPromise()
  }
}