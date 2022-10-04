package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import org.jetbrains.annotations.TestOnly
import java.util.*

@State(name = "SettingsSyncLocalSettings", storages = [Storage("settingsSyncLocal.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
internal class SettingsSyncLocalSettings : SimplePersistentStateComponent<SettingsSyncLocalSettings.State>(State()) {

  companion object {
    fun getInstance(): SettingsSyncLocalSettings = ApplicationManager.getApplication().getService(SettingsSyncLocalSettings::class.java)
  }

  class State : BaseState() {
    var applicationId: String? by string(UUID.randomUUID().toString())
    var knownAndAppliedServerId: String? by string(null)

    @TestOnly
    internal fun reset() {
      applicationId = UUID.randomUUID().toString()
      knownAndAppliedServerId = null
    }
  }

  val applicationId: UUID get() = UUID.fromString(state.applicationId)

  var knownAndAppliedServerId
    get() = state.knownAndAppliedServerId
    set(value) {
      state.knownAndAppliedServerId = value
    }
}