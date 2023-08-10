// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent

/**
 * This is the intended misuse of [LanguageRuntimeConfiguration] concept. This class is for passing the target introspection results to the
 * panels that configure different Python virtual envs.
 */
class PythonLanguageRuntimeConfiguration : LanguageRuntimeConfiguration(PythonLanguageRuntimeType.TYPE_ID),
                                           PersistentStateComponent<PythonLanguageRuntimeConfiguration.State> {
  var pythonInterpreterPath: String = ""
  var userHome: String = ""

  class State : BaseState() {
    var pythonInterpreterPath by string()
    var userHome by string()
  }

  override fun getState(): State = State().also {
    it.pythonInterpreterPath = pythonInterpreterPath
    it.userHome = userHome
  }

  override fun loadState(state: State) {
    pythonInterpreterPath = state.pythonInterpreterPath.orEmpty()
    userHome = state.userHome.orEmpty()
  }
}