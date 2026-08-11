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
import com.intellij.python.pyproject.model.PyProjectModelSettings.FeatureState.ASK
import com.intellij.python.pyproject.model.PyProjectModelSettings.FeatureState.OFF
import com.intellij.python.pyproject.model.PyProjectModelSettings.FeatureState.ON
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService
import com.intellij.python.pyproject.statistics.PyProjectTomlCollector


@Service(Service.Level.PROJECT)
@State(name = "PyProjectModelSettings", storages = [Storage("pyProjectModel.xml")])
class PyProjectModelSettings(private val project: Project) :
  PersistentStateComponent<PyProjectModelSettings.State>, Disposable {
  override fun dispose() {}
  class State : BaseState() {
    /**
     * `null` means the user hasn't made an explicit choice yet, so the effective default depends on
     * [featureStateInRegistry] (`true` for [ON], `false` for [ASK]). See [PyProjectModelSettings.usePyprojectToml].
     */
    var usePyprojectToml: Boolean? by property(null) { it == null }
    var showConfigurationNotification: Boolean by property(true)
  }

  private var myState = State()


  var usePyprojectToml: Boolean
    get() = when (featureStateInRegistry) {
      OFF -> false
      // `ON` enables the feature by default but the user may still turn it off; `ASK` is off until enabled.
      ON, ASK -> myState.usePyprojectToml ?: enabledByDefault
    }
    set(value) {
      if (usePyprojectToml == value) return
      // Persist an explicit choice only when it deviates from the registry default; otherwise keep it unset
      // (null) so it's omitted from storage and keeps following the default. See [State.usePyprojectToml].
      myState.usePyprojectToml = value.takeIf { it != enabledByDefault }
      project.service<PyProjectAutoImportService>().apply {
        PyProjectTomlCollector.pyProjectBasedModelModeChanged(value)
        if (value) {
          start()
        }
        else {
          stop()
        }
      }
    }

  /** `true` when the feature is enabled by default for the current [featureStateInRegistry] (only for [ON]). */
  private val enabledByDefault: Boolean
    get() = when (featureStateInRegistry) {
      ON -> true
      ASK, OFF -> false
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
     * Hard setting controlling the feature on the registry level (see [FeatureState]).
     * For the effective per-project state (registry default + user choice) use [PyProjectModelSettings.usePyprojectToml].
     */
    val featureStateInRegistry: FeatureState get() = Registry.get("intellij.python.pyproject.model").asEnum(FeatureState.entries)
  }

  enum class FeatureState {
    /**
     * Convert `pyproject.toml` to modules by default (without asking the user), but let the user turn it off.
     */
    ON,

    /**
     * Never convert `pyproject.toml` to modules
     */
    OFF,

    /**
     * Off by default. Look for `pyproject.toml` and, if any, ask user. Then, store the answer on project level. See [PyProjectModelSettings]
     */
    ASK
  }
}
