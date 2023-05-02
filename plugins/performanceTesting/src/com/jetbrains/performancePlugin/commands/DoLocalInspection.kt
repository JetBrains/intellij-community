package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.ui.TypingTarget
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.jetbrains.performancePlugin.PerformanceTestSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.KeyboardFocusManager
import kotlin.coroutines.resume

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
    checkFocusInEditor(context)

    val busConnection = project.messageBus.simpleConnect()
    val span = PerformanceTestSpan.getTracer(isWarmupMode()).spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext())
    var spanRef: Span? = null
    var scopeRef: Scope? = null
    suspendCancellableCoroutine { continuation ->
      busConnection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
        override fun daemonFinished() {
          val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
          val document = FileEditorManager.getInstance(project).selectedTextEditor!!.document
          val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
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
        DaemonCodeAnalyzer.getInstance(project).restart()
      }
    }
  }

  private suspend fun checkFocusInEditor(context: PlaybackContext) {
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

      throw IllegalStateException("There is no focus in editor (focusOwner=$focusOwner)")
    }
  }

  private fun isWarmupMode(): Boolean {
    return text.contains("WARMUP")
  }
}
