package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.ui.playback.commands.AlphaNumericTypeCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class DoLocalInspection(text: String, line: Int) : AbstractCommand(text, line), Disposable {
  override fun _execute(context: PlaybackContext): Promise<Any> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val project = context.project
    getInstance(project).waitForSmartMode()
    ApplicationManager.getApplication().invokeAndWait {
      val target = AlphaNumericTypeCommand.findTarget(context)
      if (target == null) {
        actionCallback.reject("There is no focus in editor")
      }
    }
    if (actionCallback.isRejected) {
      return actionCallback.toPromise()
    }
    val busConnection = project.messageBus.connect()
    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext())
    val spanRef = Ref<Span>()
    val scopeRef = Ref<Scope>()
    busConnection.subscribe<DaemonListener>(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
      override fun daemonFinished() {
        val myDaemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
        if (myDaemonCodeAnalyzer.isErrorAnalyzingFinished(psiFile)) {
          val errorsOnHighlighting = DaemonCodeAnalyzerImpl.getHighlights(
            editor.document, HighlightSeverity.ERROR, project)
          val warningsOnHighlighting = DaemonCodeAnalyzerImpl.getHighlights(
            editor.document, HighlightSeverity.WARNING, project)
          val weakWarningsOnHighlighting = DaemonCodeAnalyzerImpl.getHighlights(
            editor.document, HighlightSeverity.WEAK_WARNING, project)
          val finishMessage = StringBuilder("Local inspections have been finished with: ")
          spanRef.get().setAttribute("Errors", errorsOnHighlighting.size.toLong())
          if (!errorsOnHighlighting.isEmpty()) {
            finishMessage.append("\n").append(java.lang.String("Errors: " + errorsOnHighlighting.size) as String)
          }
          for (error in errorsOnHighlighting) {
            finishMessage.append("\n").append(java.lang.String(error.text + ": " + error.description) as String)
          }
          spanRef.get().setAttribute("Warnings", warningsOnHighlighting.size.toLong())
          if (!warningsOnHighlighting.isEmpty()) {
            finishMessage.append("\n").append(java.lang.String("Warnings: " + warningsOnHighlighting.size) as String)
          }
          for (warning in warningsOnHighlighting) {
            finishMessage.append("\n").append(java.lang.String(warning.text + ": " + warning.description) as String)
          }
          spanRef.get().setAttribute("Weak Warnings", warningsOnHighlighting.size.toLong())
          if (!weakWarningsOnHighlighting.isEmpty()) {
            finishMessage.append("\n").append(
              java.lang.String("Weak Warnings: " + weakWarningsOnHighlighting.size) as String)
          }
          for (weakWarning in weakWarningsOnHighlighting) {
            finishMessage.append("\n").append(
              java.lang.String(weakWarning.text + ": " + weakWarning.description) as String)
          }
          spanRef.get().end()
          scopeRef.get().close()
          busConnection.disconnect()
          context.message(finishMessage.toString(), line)
          actionCallback.setDone()
        }
      }
    })
    getInstance(project).smartInvokeLater {
      PsiManager.getInstance(project).dropPsiCaches()
      context.message("Local inspections have been started", line)
      spanRef.set(span.startSpan())
      scopeRef.set(spanRef.get().makeCurrent())
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
    return actionCallback.toPromise()
  }

  override fun dispose() {}

  companion object {
    const val PREFIX = CMD_PREFIX + "doLocalInspection"
    const val SPAN_NAME = "localInspections"
  }
}
