package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.ui.TypingTarget
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.ui.UIUtil
import com.jetbrains.performancePlugin.PerformanceTestSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.KeyboardFocusManager
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Command runs local inspection.
 * Runs local inspection using DaemonCodeAnalyzer.
 */
class DoLocalInspection(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "doLocalInspection"
    const val SPAN_NAME: String = "localInspections"
  }

  @Suppress("TestOnlyProblems")
  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    project.waitForSmartMode()
    withTimeout(40.seconds) {
      checkFocusInEditor(context, project)
    }

    val busConnection = project.messageBus.simpleConnect()
    val span = PerformanceTestSpan.getTracer(isWarmupMode()).spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext())
    var spanRef: Span? = null
    var scopeRef: Scope? = null
    suspendCancellableCoroutine { continuation ->
      busConnection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
        override fun daemonFinished(fileEditors: MutableCollection<out FileEditor>) {
          if (spanRef == null) {
            return
          }
          val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
          val document = FileEditorManager.getInstance(project).selectedTextEditor!!.document
          val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
          if (fileEditors.find { editor -> editor.file.name == psiFile.name } == null) {
            return
          }
          if (!daemonCodeAnalyzer.isErrorAnalyzingFinished(psiFile)) {
            return
          }

          val errorsOnHighlighting = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.ERROR, project)
          val warningsOnHighlighting = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project)
          val weakWarningsOnHighlighting = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WEAK_WARNING, project)
          val finishMessage = StringBuilder("Local inspections have been finished with: ")
          spanRef!!.setAttribute("Errors", errorsOnHighlighting.size.toLong())
          if (!errorsOnHighlighting.isEmpty()) {
            finishMessage.append("\n").append("Errors: ${errorsOnHighlighting.size}")
          }
          for (error in errorsOnHighlighting) {
            finishMessage.append("\n").append("${error.text}: ${error.description}")
          }
          spanRef!!.setAttribute("Warnings", warningsOnHighlighting.size.toLong())
          if (!warningsOnHighlighting.isEmpty()) {
            finishMessage.append("\n").append("Warnings: ${warningsOnHighlighting.size}")
          }
          for (warning in warningsOnHighlighting) {
            finishMessage.append("\n").append("${warning.text}: ${warning.description}")
          }
          spanRef!!.setAttribute("Weak Warnings", warningsOnHighlighting.size.toLong())
          if (!weakWarningsOnHighlighting.isEmpty()) {
            finishMessage.append("\n").append("Weak Warnings: ${weakWarningsOnHighlighting.size}")
          }
          for (weakWarning in weakWarningsOnHighlighting) {
            finishMessage.append("\n").append("${weakWarning.text}: ${weakWarning.description}")
          }
          spanRef!!.end()
          scopeRef!!.close()
          busConnection.disconnect()
          context.message(finishMessage.toString(), line)
          continuation.resume(Unit)
        }
      })

      DumbService.getInstance(project).smartInvokeLater {
        PsiManager.getInstance(project).dropPsiCaches()
        context.message("Local inspections have been started", line)
        spanRef = span.startSpan()
        scopeRef = spanRef!!.makeCurrent()
        val document = FileEditorManager.getInstance(project).selectedTextEditor!!.document
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
        DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
      }
    }
  }

  private suspend fun checkFocusInEditor(context: PlaybackContext, project: Project) {
    if (!context.isUseTypingTargets) {
      return
    }

    withContext(Dispatchers.EDT) {
      val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
      var each = focusOwner
      while (each != null) {
        if (each is TypingTarget) {
          return@withContext
        }
        each = each.parent
      }

      val editorTracker = EditorTracker.getInstance(project)
      val message = "There is no focus in editor (focusOwner=${
        focusOwner?.let {
          UIUtil.uiParents(it, false).joinToString(separator = "\n  ->\n")
        }
      },\neditorTracker=$editorTracker)"

      takeScreenshotOfAllWindows("no-focus-in-editor")

      if (focusOwner == null) {
        val activeEditors = editorTracker.activeEditors
        activeEditors.firstOrNull()?.let {
          it.contentComponent.requestFocusInWindow()
          logger<DoLocalInspection>().warn(message)
          return@withContext
        }
      }
      throw IllegalStateException(message)
    }
  }

  private fun isWarmupMode(): Boolean {
    return text.contains("WARMUP")
  }
}
