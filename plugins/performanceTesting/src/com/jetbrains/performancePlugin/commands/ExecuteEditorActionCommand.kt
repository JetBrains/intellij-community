package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx.Companion.getInstanceEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.Ref
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerListener
import com.jetbrains.performancePlugin.utils.EditorUtils.createEditorContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

class ExecuteEditorActionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = AbstractCommand.CMD_PREFIX + "executeEditorAction"
    const val PARTITION_SPAN_NAME: @NonNls String = "execute_editor_"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val input = extractCommandArgument(DelayTypeCommand.PREFIX)
    val parameter = input.parameter(1)
    val span = PerformanceTestSpan.TRACER.spanBuilder(PARTITION_SPAN_NAME + cleanSpanName(parameter)).setParent(
      PerformanceTestSpan.getContext())
    val spanRef = Ref<Span>()
    val scopeRef = Ref<Scope>()
    val connection = context.project.messageBus.simpleConnect()
    val project = context.project
    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    if (editor == null) {
      throw IllegalStateException("editor is null")
    }
    withContext(Dispatchers.EDT) {
      spanRef.set(span.startSpan())
      scopeRef.set(spanRef.get().makeCurrent())
      executeAction(editor, parameter)
      val job = DaemonCodeAnalyzerListener.listen(connection, spanRef, scopeRef)
      job.waitForComplete()
    }
  }

  fun executeAction(editor: Editor, actionId: String) {
    val actionManager = getInstanceEx()
    val action = actionManager.getAction(actionId)
    if (action == null) {
      throw IllegalArgumentException("fail to find action '$actionId'")
    }
    executeAction(editor, action)
  }

  fun executeAction(editor: Editor, action: AnAction) {
    val event = AnActionEvent.createFromAnAction(action, null, "", createEditorContext(editor))
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
    else {
      throw IllegalStateException("Cant execute action $action")
    }
  }
}

private fun String.parameter(i: Int): String {
  return this.split(" ").also {
    if (it.size < i + 1) throw IllegalArgumentException("Parameter with index $i not exists in $it")
  }[i].trim()
}

private fun cleanSpanName(name: String): String {
  return name.lowercase().replace("$", "")
}