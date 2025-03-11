// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Service(Service.Level.APP)
@State(
  name = "PythonAddNewEnvironmentState",
  storages = [Storage(value = StoragePathMacros.APP_INTERNAL_STATE_DB)]
)
class PythonAddNewEnvironmentState : SimplePersistentStateComponent<PythonAddNewEnvironmentState.State>(State()) {
  class State : BaseState() {
    var isFirstVisit: Boolean by property(true)
  }

  var isFirstVisit: Boolean
    get() = state.isFirstVisit
    set(value) {
      state.isFirstVisit = value
    }

  companion object {
    fun getInstance(): PythonAddNewEnvironmentState =
      ApplicationManager.getApplication().getService(PythonAddNewEnvironmentState::class.java)
  }
}