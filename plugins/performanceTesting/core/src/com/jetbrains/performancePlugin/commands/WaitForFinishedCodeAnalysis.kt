package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.fileEditor.impl.waitForFullyLoaded
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.toPromise
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.readLines

class WaitForFinishedCodeAnalysis(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "waitForFinishedCodeAnalysis"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val fileEditorManager = FileEditorManager.getInstance(project)
    for (openFile in fileEditorManager.openFiles) {
      fileEditorManager.getComposite(openFile)?.waitForFullyLoaded()
    }
    project
      .service<WaitForFinishedCodeAnalysisListener.ListenerState>()
      .highlightingFinishedEverywhere
      .toPromise()
      .await()
  }

  override fun getName(): String {
    return PREFIX
  }
}

internal class WaitForFinishedCodeAnalysisListener(private val project: Project): DaemonCodeAnalyzer.DaemonListener {
  @Service(Service.Level.PROJECT)
  class ListenerState {
    val sessions = ConcurrentHashMap<FileEditor, Exception?>()
    val highlightingFinishedEverywhere = ActionCallback()
  }

  private companion object {
    val LOG = logger<WaitForFinishedCodeAnalysisListener>()
  }

  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    if (!ApplicationManagerEx.isInIntegrationTest() || skipNonPsiFileEditors(fileEditors)) return
    val state = project.service<ListenerState>()
    val editor = fileEditors.first()
    LOG.info("daemon starting for $editor")
    val previousSessionStartTrace = state.sessions.put(editor, Exception("Previous daemon start trace (editors = $fileEditors)"))
    if (previousSessionStartTrace != null) {
      val errMsg = "Overlapping highlighting sessions"
      val err = AssertionError(errMsg)
      err.addSuppressed(Exception("Current daemon start trace (editors = $fileEditors)"))
      err.addSuppressed(previousSessionStartTrace)
      LOG.error(err)
    }
  }

  override fun daemonCanceled(reason: String, fileEditors: Collection<FileEditor>) {
    daemonStopped(fileEditors, true, reason)
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    daemonStopped(fileEditors, false, "daemonFinished")
  }

  private fun daemonStopped(fileEditors: Collection<FileEditor>, canceled: Boolean, reason: String) {
    if (!ApplicationManagerEx.isInIntegrationTest() || skipNonPsiFileEditors(fileEditors)) return
    val state = project.service<ListenerState>()
    val editor = fileEditors.first()
    state.sessions.remove(editor)
    LOG.info("daemon stopped for $editor with reason $reason; canceled = $canceled")

    val fileEditor = fileEditors.filterIsInstance<TextEditor>().firstOrNull()!!
    val entireFileHighlighted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(fileEditor, project)

    if (!canceled && entireFileHighlighted && !DumbService.isDumb(project)) {
      invokeLater {

        val rowFirstDateTimeFromLog = Paths.get(PathManager.getLogPath(), "idea.log")
          .readLines()
          .first()
          .substringBefore("[")
          .replace(",", ".")
          .trim()
        val dateTimeWhenAppStarted = LocalDateTime.parse(rowFirstDateTimeFromLog,
                                                         DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        LOG.info("Total opening time is : ${ChronoUnit.MILLIS.between(dateTimeWhenAppStarted, LocalDateTime.now())}")

        if (state.sessions.isEmpty()) {
          state.highlightingFinishedEverywhere.setDone()
        }
      }
    }
  }

  private fun skipNonPsiFileEditors(fileEditors: Collection<FileEditor>): Boolean = fileEditors.none { editor -> editor is PsiAwareTextEditorImpl }
}