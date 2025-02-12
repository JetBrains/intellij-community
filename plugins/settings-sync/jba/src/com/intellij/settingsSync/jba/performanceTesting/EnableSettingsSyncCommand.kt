package com.intellij.settingsSync.jba.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.config.SettingsSyncEnabler
import kotlinx.coroutines.*
import org.jetbrains.annotations.NonNls
import java.util.concurrent.TimeUnit

enum class EnableSettingSyncOptions {
  GET, PUSH, NONE
}

class EnableSettingsSyncCommand(text: @NonNls String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "enableSettingsSync"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    withContext(Dispatchers.EDT) {
      val (crossIdeSync, action) = extractCommandArgument(PREFIX).split(" ")
      val isCrossIdeSync = crossIdeSync.toBoolean()
      val enableSyncOptions: EnableSettingSyncOptions = EnableSettingSyncOptions.valueOf(action)

      val settingsSyncEnabler = SettingsSyncEnabler()
      val serverRespondedOnCheck = CompletableDeferred<Boolean>()

      settingsSyncEnabler.addListener(object : SettingsSyncEnabler.Listener {
        override fun serverStateCheckFinished(state: UpdateResult) {
          when (state) {
            is UpdateResult.Success -> {
              when (enableSyncOptions) {
                EnableSettingSyncOptions.GET -> {
                  settingsSyncEnabler.getSettingsFromServer()
                }
                EnableSettingSyncOptions.PUSH -> {
                  settingsSyncEnabler.pushSettingsToServer()
                }
                else -> {
                  throw Exception(
                    "Settings Sync is already initialized, either ${EnableSettingSyncOptions.GET} or ${EnableSettingSyncOptions.PUSH} should be provided, now, ${EnableSettingSyncOptions.NONE}")
                }
              }
              SettingsSyncSettings.getInstance().syncEnabled = true
            }
            is UpdateResult.NoFileOnServer, UpdateResult.FileDeletedFromServer -> {
              when (enableSyncOptions) {
                EnableSettingSyncOptions.GET, EnableSettingSyncOptions.PUSH -> {
                  throw Exception("Settings Sync is not initialized ${EnableSettingSyncOptions.NONE} should be used")
                }
                else -> {}
              }
              SettingsSyncSettings.getInstance().syncEnabled = true
              settingsSyncEnabler.pushSettingsToServer()
            }
            else -> {
              throw Exception(state.toString())
            }
          }
          serverRespondedOnCheck.complete(true)
        }
      })
      settingsSyncEnabler.checkServerStateAsync()
      serverRespondedOnCheck.await()

      var startTime = System.currentTimeMillis()
      while (!SettingsSyncMain.getInstance().controls.bridge.isInitialized) {
        delay(50)
        if (System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(10)) {
          throw Exception("Settings Sync initialization timeout exceeded")
        }
      }

      if(SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled != isCrossIdeSync){
        SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled = isCrossIdeSync
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.CrossIdeSyncStateChanged(isCrossIdeSync))
      }
      //there is no event that cross-ide sync was enabled, so we need to check that file appears and wait a bit :(
      startTime = System.currentTimeMillis()
      while (RemoteCommunicatorHolder.getRemoteCommunicator() != null &&
             RemoteCommunicatorHolder.getRemoteCommunicator()?.isFileExists(CROSS_IDE_SYNC_MARKER_FILE) != isCrossIdeSync) {
        delay(500)
        if (System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(5)) {
          val fileExists = RemoteCommunicatorHolder.getRemoteCommunicator()?.isFileExists(CROSS_IDE_SYNC_MARKER_FILE)
          throw Exception("Cross-IDE sync marker file was not updated in 5 seconds. File exists=$fileExists, expected=$isCrossIdeSync")
        }
      }
    }
  }
}