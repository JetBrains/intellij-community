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
import com.intellij.python.pyproject.model.PyProjectModelSettings.Companion.isEnabledByUserAndRegistry
import com.intellij.python.pyproject.model.PyProjectModelSettings.FeatureState.ASK
import com.intellij.python.pyproject.model.PyProjectModelSettings.FeatureState.OFF
import com.intellij.python.pyproject.model.PyProjectModelSettings.FeatureState.ON
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService


@Service(Service.Level.PROJECT)
@State(name = "PyProjectModelSettings", storages = [Storage("pyProjectModel.xml")])
class PyProjectModelSettings(private val project: Project) :
  PersistentStateComponent<PyProjectModelSettings.State>, Disposable {
  override fun dispose() {}
  class State : BaseState() {
    var usePyprojectToml: Boolean by property(false)
    var showConfigurationNotification: Boolean by property(true)
  }

  private var myState = State()


  var usePyprojectToml: Boolean
    get() = when (featureStateInRegistry) {
      ON, ASK -> myState.usePyprojectToml
      OFF -> false
    }
    set(value) {
      if (myState.usePyprojectToml != value) {
        myState.usePyprojectToml = value
        project.service<PyProjectAutoImportService>().apply {
          if (value) {
            start()
          }
          else {
            stop()
          }
        }
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

    /**
     * Auto-import (converting `pyproject.toml` to modules) enabled *both* in registry and on user level.
     */
    fun isEnabledByUserAndRegistry(project: Project): Boolean =
      when (featureStateInRegistry) {
        ON -> true
        OFF -> false
        ASK -> getInstance(project).usePyprojectToml
      }

    /**
     * Hard setting: if disabled -> feature is disabled on the Registry.
     * For user-defined setting, check [isEnabledByUserAndRegistry] or (more low-level) [PyProjectModelSettings.usePyprojectToml].
     * Be sure to check **both** (by means of [isEnabledByUserAndRegistry]) except for UI for the aforementioned service.
     */
    val featureStateInRegistry: FeatureState get() = Registry.get("intellij.python.pyproject.model").asEnum(FeatureState.entries)
  }

  enum class FeatureState {
    /**
     * Always convert `pyproject.toml` to modules
     */
    ON,

    /**
     * Never convert `pyproject.toml` to modules
     */
    OFF,

    /**
     * Look for `pyproject.toml` and, if any, ask user. Then, store the answer on project level. See [PyProjectModelSettings]
     */
    ASK
  }
}
