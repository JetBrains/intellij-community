// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@State(
  name = "SpaceServerConfigurable",
  storages = [Storage(value = "SpaceServer.xml", roamingType = RoamingType.DEFAULT)]
)
class SpaceSettings : SimplePersistentStateComponent<SpaceSettingsState>(SpaceSettingsState()) {
  var serverSettings: SpaceServerSettings
    get() = state.serverSettings
    set(value) {
      state.serverSettings = value
    }

  var cloneType: CloneType
    get() = state.cloneType
    set(value) {
      state.cloneType = value
    }

  companion object {
    fun getInstance(): SpaceSettings = ApplicationManager.getApplication().getService(SpaceSettings::class.java)
  }
}

class SpaceSettingsState : BaseState() {
  var serverSettings: SpaceServerSettings by this.property(SpaceServerSettings()) {
    it.enabled.not() && it.server.isBlank()
  }

  var cloneType: CloneType by enum(CloneType.HTTPS)
}

enum class CloneType {
  HTTPS,
  SSH
}
