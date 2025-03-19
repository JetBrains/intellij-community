package com.intellij.performanceTesting.frontend.commands

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.platform.recentFiles.frontend.Switcher
import com.intellij.platform.recentFiles.frontend.createAndShowNewSwitcherSuspend
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * Usage: %showRecentFiles <seconds to wait before close>
 */
internal class ShowRecentFilesCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: @NonNls String = "showRecentFiles"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val secondsToWaitBeforeClose = extractCommandArgument(PREFIX).runCatching { this.toInt() }.getOrDefault(5)
    withContext(Dispatchers.EDT) {
      val alreadyOpenedSwitcher = Switcher.SWITCHER_KEY.get(context.project)
      if (alreadyOpenedSwitcher != null) {
        alreadyOpenedSwitcher.cbShowOnlyEditedFiles?.apply { isSelected = !isSelected }
      }
      else {
        createAndShowNewSwitcherSuspend(false, null, IdeBundle.message("title.popup.recent.files"), context.project)
      }
      delay(secondsToWaitBeforeClose * 1000L)

      Switcher.SWITCHER_KEY.get(context.project)?.cancel()
    }
  }

  override fun getName(): String {
    return NAME
  }
}