// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.DataManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.LatencyRecorder
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.utils.EditorUtils
import com.jetbrains.performancePlugin.utils.findTypingTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Command runs an editor action several times with a delay after each run, and records the typing latency of each run.
 * The metric is the one of [DelayTypeCommand]: the `latency#*` attributes of its [DelayTypeCommand.SPAN_NAME] span.
 * A non-empty clipboard text goes to the clipboard before the first run.
 * The clipboard text is not the last argument, because the command text loses its trailing whitespace.
 * Syntax: %delayEditorAction <delay in ms>|<action id>|<clipboard text>|<times>
 * Example: %delayEditorAction 150|EditorPaste|pasted |15
 */
class DelayEditorActionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = CMD_PREFIX + "delayEditorAction"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val arguments = extractCommandArgument(PREFIX).split('|')
    require(arguments.size == 4) {
      "Wrong arguments, the command should be: %delayEditorAction <delay in ms>|<action id>|<clipboard text>|<times>"
    }
    val interval = arguments[0].toLong().milliseconds
    val actionId = arguments[1]
    val clipboardText = arguments[2]
    val times = arguments[3].toInt()
    val action = requireNotNull(ActionManager.getInstance().getAction(actionId)) { "No action '$actionId'" }

    withContext(Dispatchers.EDT) {
      ProjectUtil.focusProjectWindow(project, true)
    }
    val editor = withTimeout(10.seconds) {
      var editor = findTypingEditor(project)
      while (editor == null) {
        delay(1.seconds)
        editor = findTypingEditor(project)
      }
      editor
    }
    if (clipboardText.isNotEmpty()) {
      // the clipboard of a split or light frontend takes contents on the EDT only
      withContext(Dispatchers.EDT) {
        CopyPasteManager.getInstance().setContents(StringSelection(clipboardText))
      }
    }

    withTypingLatencySpan { span ->
      repeat(times) {
        withContext(Dispatchers.EDT) {
          span.addEvent("Action $actionId")
          writeIntentReadAction {
            // the action gets no input event, so it records no latency itself
            LatencyRecorder.getInstance().recordLatencyAwareAction(editor, actionId, System.currentTimeMillis())
            val dataContext = EditorUtils.createEditorContext(editor)
            val event = AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null)
            ActionUtil.updateAction(action, event)
            check(ActionUtil.performAction(action, event).isPerformed) { "Action '$actionId' is not performed" }
          }
        }
        // also after the last run: the repaint that gives its sample comes later
        delay(interval)
      }
    }
  }

  private suspend fun findTypingEditor(project: Project): Editor? = withContext(Dispatchers.EDT) {
    val typingTarget = findTypingTarget(project) ?: return@withContext null
    DataManager.getInstance().getDataContext(typingTarget as? JComponent).getData(CommonDataKeys.EDITOR)
  }
}
