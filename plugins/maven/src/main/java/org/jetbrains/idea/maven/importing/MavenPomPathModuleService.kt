// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.importing.MavenPomPathModuleService.MavenPomPathState

@State(name = "MavenCustomPomFilePath")
class MavenPomPathModuleService : SimplePersistentStateComponent<MavenPomPathState>(MavenPomPathState()) {
  companion object {
    @JvmStatic
    fun getInstance(module: Module): MavenPomPathModuleService {
      return module.getService(MavenPomPathModuleService::class.java)
    }
  }

  var pomFileUrl: String?
    get() = state.mavenPomFileUrl
    set(value) {
      state.mavenPomFileUrl = value
    }

  class MavenPomPathState : BaseState() {
    var mavenPomFileUrl by string()
  }
}