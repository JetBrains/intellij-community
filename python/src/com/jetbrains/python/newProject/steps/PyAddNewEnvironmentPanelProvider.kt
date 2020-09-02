// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.steps

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAddNewEnvironmentPanelProvider {

  fun createPanel(project: Project?,
                  module: Module?,
                  existingSdks: List<Sdk>,
                  newProjectPath: String?,
                  context: UserDataHolder): PyAddNewEnvPanel

  companion object {
    @JvmField
    val EP_NAME  = ExtensionPointName.create<PyAddNewEnvironmentPanelProvider>("Pythonid.addNewEnvironmentPanelProvider")
  }
}