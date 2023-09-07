package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.project.DumbService
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
    LOG.info("Subscribing")
    val wasEntireFileHighlighted = Ref<Boolean>(false)
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
      @Volatile
      private var daemonStartTrace: Exception? = null

      override fun daemonStarting(fileEditors: Collection<FileEditor>) {
        if (skipNonPsiFileEditors(fileEditors)) return
        if (daemonStartTrace != null) {
          val errMsg = "Overlapping highlighting sessions"
          val err = AssertionError(errMsg)
          err.addSuppressed(Exception("Current daemon start trace (editors = $fileEditors)"))
          err.addSuppressed(daemonStartTrace)
          LOG.error(err)
          actionCallback.reject(errMsg)
        }
        daemonStartTrace = Exception("Previous daemon start trace (editors = $fileEditors)")
      }

      override fun daemonCanceled(reason: String, fileEditors: Collection<FileEditor>) {
        daemonStopped(fileEditors, true)
      }

      override fun daemonFinished(fileEditors: Collection<FileEditor>) {
        daemonStopped(fileEditors, false)
      }

      private fun daemonStopped(fileEditors: Collection<FileEditor>, canceled: Boolean) {
        if (skipNonPsiFileEditors(fileEditors)) return
        daemonStartTrace = null
        val fileEditor = fileEditors.filterIsInstance<TextEditor>().firstOrNull()!!
        val entireFileHighlighted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(fileEditor, project)

        if (!canceled && entireFileHighlighted && !DumbService.isDumb(project)) {
          invokeLater {
            wasEntireFileHighlighted.set(entireFileHighlighted)

            val rowFirstDateTimeFromLog = Paths.get(PathManager.getLogPath(), "idea.log")
              .readLines()
              .first()
              .substringBefore("[")
              .replace(",", ".")
              .trim()
            val dateTimeWhenAppStarted = LocalDateTime.parse(rowFirstDateTimeFromLog,
                                                             DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            LOG.info("Total opening time is : ${ChronoUnit.MILLIS.between(dateTimeWhenAppStarted, LocalDateTime.now())}")

            actionCallback.setDone()
          }
        }
      }
    })

    return actionCallback.toPromise()
  }

  private fun skipNonPsiFileEditors(fileEditors: Collection<FileEditor>): Boolean =
    fileEditors.none { editor -> editor is PsiAwareTextEditorImpl }
}