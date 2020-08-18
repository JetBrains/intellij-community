// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property

// To be removed in 2021.1
@State(name = "TerminalProjectOptionsProvider", storages = [(Storage("terminal.xml"))])
class TerminalProjectOptionsProviderOld(val project: Project) : PersistentStateComponent<TerminalProjectOptionsProviderOld.State> {

  private var myState : State? = State()

  override fun getState(): State? {
    return myState
  }

  override fun loadState(state: State) {
    myState = state
  }

  fun getAndClear(): State? {
    val copy = state
    myState = null
    return copy
  }

  class State {
    var myStartingDirectory: String? = null
    var myShellPath: String? = null
    @get:Property(surroundWithTag = false, flat = true)
    var envDataOptions = EnvironmentVariablesDataOptions()
  }
}
