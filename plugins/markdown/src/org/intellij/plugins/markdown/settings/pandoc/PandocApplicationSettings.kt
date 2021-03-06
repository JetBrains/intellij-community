// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings.pandoc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "Pandoc.Application.Settings", storages = [Storage(value = "pandoc.xml", roamingType = RoamingType.PER_OS)])
class PandocApplicationSettings: PersistentStateComponent<PandocApplicationSettings.State> {
  private var myState = State()

  companion object {
    fun getInstance(): PandocApplicationSettings {
      return ApplicationManager.getApplication().getService(PandocApplicationSettings::class.java)
    }
  }

  class State {
    internal var myPathToPandoc: String? = null
  }

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }
}
