package com.intellij.settingsSync.config

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.settingsSync.*
import com.intellij.util.EventDispatcher
import java.util.*

internal class SettingsSyncEnabler {
  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  object State {
    val CANCELLED = ServerState.Error("Cancelled")
  }

  fun checkServerState() {
    eventDispatcher.multicaster.serverStateCheckStarted()
    val communicator = SettingsSyncMain.getInstance().getRemoteCommunicator()
    object : Task.Modal(null, SettingsSyncBundle.message("enable.sync.check.server.data.progress"), true) {
      private lateinit var serverState: ServerState

      override fun run(indicator: ProgressIndicator) {
        serverState = communicator.checkServerState()
      }

      override fun onCancel() {
        serverState = State.CANCELLED
      }

      override fun onFinished() {
        eventDispatcher.multicaster.serverStateCheckFinished(serverState)
      }
    }.queue()
  }


  fun getSettingsFromServer() {
    eventDispatcher.multicaster.updateFromServerStarted()
    val settingsSyncControls = SettingsSyncMain.getInstance().controls
    object : Task.Modal(null, SettingsSyncBundle.message("enable.sync.get.from.server.progress"), false) {
      private lateinit var updateResult: UpdateResult

      override fun run(indicator: ProgressIndicator) {
        val result = settingsSyncControls.remoteCommunicator.receiveUpdates()
        updateResult = result
        if (result is UpdateResult.Success) {
          val cloudEvent = SyncSettingsEvent.CloudChange(result.settingsSnapshot, result.serverVersionId)
          settingsSyncControls.bridge.initialize(SettingsSyncBridge.InitMode.TakeFromServer(cloudEvent))
        }
      }

      override fun onFinished() {
        eventDispatcher.multicaster.updateFromServerFinished(updateResult)
      }
    }.queue()
  }


  fun pushSettingsToServer() {
    val settingsSyncControls = SettingsSyncMain.getInstance().controls
    object: Task.Modal(null, SettingsSyncBundle.message("enable.sync.push.to.server.progress"), false) {
      override fun run(indicator: ProgressIndicator) {
        // todo initialization must be modal but pushing to server can be made later
        settingsSyncControls.bridge.initialize(SettingsSyncBridge.InitMode.PushToServer)
      }
    }.queue()
  }


  fun addListener(listener: Listener) {
    eventDispatcher.addListener(listener)
  }

  interface Listener : EventListener {
    fun serverStateCheckStarted() {
      serverRequestStarted()
    }

    fun serverStateCheckFinished(state: ServerState) {
      serverRequestFinished()
    }

    fun updateFromServerStarted() {
      serverRequestStarted()
    }

    fun updateFromServerFinished(result: UpdateResult) {
      serverRequestFinished()
    }

    fun serverRequestStarted() {}
    fun serverRequestFinished() {}
  }
}