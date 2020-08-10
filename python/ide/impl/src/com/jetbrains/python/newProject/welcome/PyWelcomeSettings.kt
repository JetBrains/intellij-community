// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.welcome

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(name = "PyWelcomeSettings", storages = [Storage("pyWelcome.xml")], reportStatistic = true)
class PyWelcomeSettings : PersistentStateComponent<PyWelcomeSettings.State> {
  companion object {
    @JvmStatic
    val instance: PyWelcomeSettings
      get() = ServiceManager.getService(PyWelcomeSettings::class.java)
  }

  private val state: State = State()

  var createWelcomeScriptForEmptyProject: Boolean
    get() = state.CREATE_WELCOME_SCRIPT_FOR_EMPTY_PROJECT
    set(value) {
      state.CREATE_WELCOME_SCRIPT_FOR_EMPTY_PROJECT = value
    }

  override fun getState(): State = state

  override fun loadState(state: State) {
    XmlSerializerUtil.copyBean(state, this.state)
  }

  @Suppress("PropertyName")
  class State {
    @JvmField
    var CREATE_WELCOME_SCRIPT_FOR_EMPTY_PROJECT: Boolean = true
  }
}