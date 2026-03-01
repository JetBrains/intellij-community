package com.intellij.settingsSync.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.settingsSync.core.CROSS_IDE_SYNC_MARKER_FILE
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncMain
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.SyncSettingsEvent
import com.intellij.settingsSync.core.UpdateResult
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.config.SettingsSyncEnabler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

      // Ensure JBA provider is available by setting up the local settings
      SettingsSyncLocalSettings.getInstance().providerCode = "jba"
      SettingsSyncLocalSettings.getInstance().userId = "test-user"
      // Force create a JBA communicator if needed
      ensureJBAProvider()

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
      // Add timeout to prevent infinite hanging
      settingsSyncEnabler.checkServerStateAsync()
      withTimeout(TimeUnit.SECONDS.toMillis(30)) {
        serverRespondedOnCheck.await()
      }

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

  private fun ensureJBAProvider() {
    // If no provider is available, this will ensure we can at least try to create one
    // The JbaCommunicatorProvider should handle mock server configuration internally
    if (RemoteCommunicatorHolder.getRemoteCommunicator() == null) {
      // This will trigger provider creation
      RemoteCommunicatorHolder.invalidateCommunicator()
    }
  }
}