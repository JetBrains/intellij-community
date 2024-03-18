package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.waitForRefresh
import com.intellij.vcs.log.impl.VcsProjectLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This command ensures that VCS log is initialized and there are no postponed refreshes (for example, when log tab is not visible).
 * @see com.intellij.vcs.log.impl.VcsLogManager.isLogUpToDate
 * @see com.intellij.vcs.log.impl.VcsLogManager.scheduleUpdate
 */
class WaitForVcsLogUpdateCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "waitForVcsLogUpdate"
    const val PREFIX = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val logManager = VcsProjectLog.getInstance(context.project).logManager ?: return
    withContext(Dispatchers.EDT) {
      if (!logManager.isLogUpToDate) logManager.waitForRefresh()
    }
  }

  override fun getName(): String = NAME
}