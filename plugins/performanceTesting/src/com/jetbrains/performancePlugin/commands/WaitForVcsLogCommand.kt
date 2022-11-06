package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.vcs.log.impl.VcsProjectLog
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class WaitForVcsLogCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "waitForGitLogIndexing"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    DumbService.getInstance(context.project).waitForSmartMode()
    VcsProjectLog.getInstance(context.project).logManager?.dataManager?.index?.addListener { root ->
      context.message("indexing for $root finished", line)
      actionCallback.setDone()
    }
    return actionCallback.toPromise()
  }
}