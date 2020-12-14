// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent

class MavenRuntimeTargetConfiguration : LanguageRuntimeConfiguration(MavenRuntimeType.TYPE_ID),
                                        PersistentStateComponent<MavenRuntimeTargetConfiguration.MyState> {
  var homePath: String = ""
  var versionString: String = ""

  override fun getState() = MyState().also {
    it.homePath = this.homePath
    it.versionString = this.versionString
  }

  override fun loadState(state: MyState) {
    this.homePath = state.homePath ?: ""
    this.versionString = state.versionString ?: ""
  }

  class MyState : BaseState() {
    var homePath by string()
    var versionString by string()
  }
}