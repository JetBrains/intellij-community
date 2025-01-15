package com.intellij.settingsSync.jba.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.settingsSync.core.config.SettingsSyncEnabler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

class PushSettingsToServerCommand(text: @NonNls String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "pushSettingsToServer"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    withContext(Dispatchers.EDT) {
      val settingsSyncEnabler = SettingsSyncEnabler()
      val serverResponded = CompletableDeferred<Boolean>()
      settingsSyncEnabler.addListener(object : SettingsSyncEnabler.Listener {
        override fun serverRequestFinished() {
          serverResponded.complete(true)
        }
      })
      settingsSyncEnabler.pushSettingsToServer()
      serverResponded.await()
    }
  }
}