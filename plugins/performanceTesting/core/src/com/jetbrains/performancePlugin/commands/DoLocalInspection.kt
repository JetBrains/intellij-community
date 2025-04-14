// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.findTypingTarget
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
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
      findTypingTarget(project)
    }

    val busConnection = project.messageBus.simpleConnect()
    val spanTag = extractCommandArgument(PREFIX).parameter("spanTag")?.let { "_$it"} ?: ""
    val span = PerformanceTestSpan.getTracer(isWarmupMode()).spanBuilder(SPAN_NAME + spanTag).setParent(PerformanceTestSpan.getContext())
    var spanRef:Span? = null
    var scopeRef: Scope? = null
    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    suspendCancellableCoroutine { continuation ->
      busConnection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
        override fun daemonStarting(fileEditors: Collection<FileEditor>) {
          val selectedDocument = FileEditorManager.getInstance(project).selectedTextEditor!!.document
          val selectedPsiFile = PsiDocumentManager.getInstance(project).getPsiFile(selectedDocument)!!
          if (fileEditors.find { editor -> editor.file.name == selectedPsiFile.name } == null) {
            return
          }
          synchronized(span) {
            if (spanRef == null) {
              spanRef = span.startSpan()
              scopeRef = spanRef.makeCurrent()
            }
          }
        }

        override fun daemonFinished(fileEditors: MutableCollection<out FileEditor>) {
          val currentSpan: Span?
          val currentScope: Scope?
          synchronized(span) {
            currentSpan = spanRef
            currentScope = scopeRef
          }
          if (currentSpan == null) {
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

          val toReport = mapOf(HighlightSeverity.ERROR to "Errors",
                               HighlightSeverity.WARNING to "Warnings",
                               HighlightSeverity.WEAK_WARNING to "Weak Warnings")
          val highlights = DaemonCodeAnalyzerImpl.getHighlights(document, toReport.keys.min(), project)
          val finishMessage = StringBuilder("Local inspections have been finished with: ")
          for (entry in toReport.entries) {
            val filtered = highlights.filter { it.severity == entry.key }
            val name = entry.value
            currentSpan.setAttribute(name, filtered.size.toLong())
            if (!filtered.isEmpty()) {
              finishMessage.append("\n").append("$name: ${filtered.size}")
              for (info in filtered) {
                finishMessage.append("\n").append("${info.text}: ${info.description}")
              }
            }
          }
          currentSpan.setAttribute("filePath", psiFile.virtualFile.path)
          currentSpan.setAttribute("linesCount", editor!!.getDocument().getLineCount().toLong())
          currentSpan.end()
          currentScope!!.close()
          busConnection.disconnect()
          context.message(finishMessage.toString(), line)
          continuation.resume(Unit)
        }
      })

      DumbService.getInstance(project).smartInvokeLater {
        PsiManager.getInstance(project).dropPsiCaches()
        context.message("Local inspections have been started", line)

        val document = FileEditorManager.getInstance(project).selectedTextEditor!!.document
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
        DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
      }
    }
  }
  private fun String.parameter(name: String): String? {
    val splitParams = this.split(" ")
    val keyIndex = splitParams.indexOf(name).takeIf { it >= 0 } ?: return null
    return splitParams.getOrNull(keyIndex + 1)
  }

  private fun isWarmupMode(): Boolean {
    return text.contains("WARMUP")
  }
}
