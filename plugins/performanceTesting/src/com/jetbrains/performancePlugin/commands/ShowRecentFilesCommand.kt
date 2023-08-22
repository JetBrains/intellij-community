package com.jetbrains.performancePlugin.commands

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.Switcher
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * Usage: %showRecentFiles <seconds to wait before close>
 */
class ShowRecentFilesCommand(text: String, line: Int): PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: @NonNls String = "showRecentFiles"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val secondsToWaitBeforeClose = extractCommandArgument(PREFIX).runCatching { this.toInt()  }.getOrDefault(5)
    withContext(Dispatchers.EDT) {
      val switcher = Switcher.SWITCHER_KEY.get(context.project)?.cbShowOnlyEditedFiles?.apply { isSelected = !isSelected }
      ?: Switcher.SwitcherPanel(context.project, IdeBundle.message("title.popup.recent.files"), null, false, true)
      delay(secondsToWaitBeforeClose * 1000L)
      if(switcher is Switcher.SwitcherPanel){
        switcher.cancel()
      }
    }
  }

  override fun getName(): String {
    return NAME
  }
}