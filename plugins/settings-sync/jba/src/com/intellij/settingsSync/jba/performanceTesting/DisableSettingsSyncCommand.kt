package com.intellij.settingsSync.jba.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.settingsSync.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DisableSettingsSyncCommand(text: @NonNls String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "disableSettingsSync"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val deleteSettings = extractCommandArgument(PREFIX).toBoolean()
    withContext(Dispatchers.EDT) {
      if (!deleteSettings) {
        SettingsSyncSettings.getInstance().syncEnabled = false
      }
      else {
        object : Task.Modal(null, SettingsSyncBundle.message("disable.remove.data.title"), false) {
          override fun run(indicator: ProgressIndicator) {
            val cdl = CountDownLatch(1)
            SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.DeleteServerData { result ->
              cdl.countDown()
              if (result is DeleteServerDataResult.Error) {
                throw Exception("Can't remove data from server")
              }
            })
            cdl.await(1, TimeUnit.MINUTES)
          }
        }.queue()
      }
    }
  }
}