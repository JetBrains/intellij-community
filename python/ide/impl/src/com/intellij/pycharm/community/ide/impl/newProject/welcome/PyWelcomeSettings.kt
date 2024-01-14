// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject.welcome

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "PyWelcomeSettings", storages = [Storage("pyWelcome.xml")], reportStatistic = true)
class PyWelcomeSettings : PersistentStateComponent<PyWelcomeSettings.State> {
  companion object {
    @JvmStatic
    val instance: PyWelcomeSettings
      get() = ApplicationManager.getApplication().getService(PyWelcomeSettings::class.java)
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