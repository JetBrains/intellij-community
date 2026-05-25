// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.CustomImlComponentService
import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.serialization.CustomImlComponentNameContributor

private const val COMPONENT_NAME = "MavenCustomPomFilePath"

class MavenPomPathModuleService(private val module: Module) {

  private val customImlComponentService = CustomImlComponentService.getInstance(module.project)

  companion object {
    @JvmStatic
    fun getInstance(module: Module): MavenPomPathModuleService {
      return MavenPomPathModuleService(module)
    }
  }

  var pomFileUrl: String?
    get() {
      return customImlComponentService.getComponentValue(module, COMPONENT_NAME, MavenPomPathState::class.java)?.mavenPomFileUrl
    }
    set(value) {
      WriteAction.run<Throwable> {
        val currentState = MavenPomPathState()
        currentState.mavenPomFileUrl = value
        customImlComponentService.setComponentValueBlocking(module, COMPONENT_NAME, currentState)
      }
    }

  class MavenPomPathState : BaseState() {
    var mavenPomFileUrl by string()
  }
}

internal class MavenPomPathModuleServiceCustomImlComponentNameContributor: CustomImlComponentNameContributor {
  override val componentName: String = COMPONENT_NAME
}
