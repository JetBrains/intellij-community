package com.jetbrains.performancePlugin.commands

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.Disposer
import com.jetbrains.performancePlugin.FakeBrowser

/**
 *
 */
class ReplaceBrowser(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = PlaybackCommandCoroutineAdapter.CMD_PREFIX + "replaceBrowser"
    val browser = FakeBrowser()
  }
  override suspend fun doExecute(context: PlaybackContext) {
    @Suppress("TestOnlyProblems")
    (ApplicationManager.getApplication() as ApplicationImpl).replaceServiceInstance(BrowserLauncher::class.java, browser, Disposer.newDisposable())
  }
  override fun getName(): String {
   return "replaceBrowser"
  }
}