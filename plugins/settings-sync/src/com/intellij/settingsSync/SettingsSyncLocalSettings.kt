package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import java.util.*

@State(name = "SettingsSyncLocalSettings", storages = [Storage("settings-sync-local.xml", roamingType = RoamingType.DISABLED)])
@Service
internal class SettingsSyncLocalSettings : SimplePersistentStateComponent<SettingsSyncLocalSettings.State>(State()) {

  companion object {
    fun getInstance(): SettingsSyncLocalSettings = ApplicationManager.getApplication().getService(SettingsSyncLocalSettings::class.java)
  }

  class State : BaseState() {
    var applicationId: String? by string(UUID.randomUUID().toString())
    var knownAndAppliedServerId: String? by string(null)
  }

  val applicationId: UUID = UUID.fromString(state.applicationId)

  var knownAndAppliedServerId = state.knownAndAppliedServerId
}