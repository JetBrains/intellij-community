package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx.Companion.getInstanceEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
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
    val expectedOpenedFile = input.parameter("expectedOpenedFile")
    val spanTag = input.parameter("spanTag")?.let { "_${it}" } ?: ""
    val span = PerformanceTestSpan.TRACER.spanBuilder(PARTITION_SPAN_NAME + cleanSpanName(parameter) + spanTag).setParent(
      PerformanceTestSpan.getContext())
    val spanRef = Ref<Span>()
    val connection = context.project.messageBus.simpleConnect()
    val project = context.project
    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    if (editor == null) {
      throw IllegalStateException("editor is null")
    }
    withContext(Dispatchers.EDT) {
      spanRef.set(span.startSpan())
      val job = DaemonCodeAnalyzerListener.listen(connection, spanRef, expectedOpenedFile = expectedOpenedFile)
      writeIntentReadAction {
        executeAction(editor, parameter)
      }
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

private fun String.parameter(name: String): String? {
  val paramArray = this.split(" ")
  val keyIndex = paramArray.indexOf(name)
  return if (keyIndex > 0 && (keyIndex + 1) <= paramArray.size) paramArray[keyIndex + 1] else null
}
private fun String.parameter(i: Int): String {
  return this.split(" ").also {
    if (it.size < i + 1) throw IllegalArgumentException("Parameter with index $i not exists in $it")
  }[i].trim()
}

private fun cleanSpanName(name: String): String {
  return name.lowercase().replace("$", "")
}