package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.commands.IdeEditorKeyCommand.pressKey
import kotlinx.coroutines.*
import org.jetbrains.annotations.NonNls
import kotlin.time.Duration.Companion.seconds

/**
 * Command chooses a completion item by name. Completion popup should be opened.
 * Example: %chooseCompletionCommand {COMPLETION_NAME}
 */
class ChooseCompletionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "chooseCompletionCommand"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val completionName = extractCommandArgument(PREFIX).trim()
    val itemsCount = getLookup(context).items.size
    for (i in 1..itemsCount) {
      //we need to get lookup every time because otherwise currentItem is not updated
      val lookup = getLookup(context)
      if (lookup.currentItem?.lookupString != completionName) {
        ApplicationManager.getApplication().invokeAndWait {
          pressKey(IdeEditorKeyCommand.EditorKey.ARROW_DOWN, context.project)
        }
      }
      else {
        withContext(Dispatchers.EDT) {
          ApplicationManager.getApplication().invokeAndWait {
            lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR)
          }
        }
        return
      }
    }
    throw IllegalArgumentException("There is no completion with name $completionName")
  }

  private fun getLookup(context: PlaybackContext): LookupImpl {
    val editor = FileEditorManager.getInstance(context.project).selectedTextEditor!!
    try {
      runBlocking {
        withTimeout(5.seconds) {
          LookupManager.getActiveLookup(editor) as LookupImpl? == null
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      throw IllegalStateException("There is no lookup after 5 seconds")
    }
    return LookupManager.getActiveLookup(editor) as LookupImpl
  }
}
