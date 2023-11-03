package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectInitialActivitiesNotifier
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

class WaitForInitialRefreshCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForInitialRefresh"
  }

  @Suppress("UsagesOfObsoleteApi")
  override fun _execute(context: PlaybackContext): Promise<Any> {
    val promise = AsyncPromise<Any>()
    @Suppress("DEPRECATION")
    context.project.coroutineScope.launch(CoroutineName("waiting for initial VFS refresh")) {
      context.project.service<ProjectInitialActivitiesNotifier>().awaitInitialVfsRefreshFinished()
      promise.setResult(null)
    }
    return promise
  }
}
