// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry


@Service(Service.Level.PROJECT)
@State(name = "PyProjectModelSettings", storages = [Storage("pyProjectModel.xml")])
class PyProjectModelSettings : PersistentStateComponent<PyProjectModelSettings.State>, Disposable {
  override fun dispose() {}
  class State : BaseState() {
    var usePyprojectToml: Boolean by property(false)
    var showConfigurationNotification: Boolean by property(true)
  }

  private var myState = State()

  var onChanged: Runnable? = null // TODO trigger logic here

  var usePyprojectToml: Boolean
    get() = isFeatureEnabled && myState.usePyprojectToml
    set(value) {
      if (myState.usePyprojectToml != value) {
        myState.usePyprojectToml = value
        onChanged?.run()
      }
    }

  var showConfigurationNotification: Boolean
    get() = myState.showConfigurationNotification
    set(value) {
      myState.showConfigurationNotification = value
    }

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PyProjectModelSettings = project.service()

    val isFeatureEnabled: Boolean get() = Registry.`is`("intellij.python.pyproject.model")
  }
}
