// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.python.newProject.steps.PyAddNewEnvironmentPanelProvider
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel

class PyPipEnvAddNewEnvironmentPanel : PyAddNewEnvironmentPanelProvider {
  override fun createPanel(project: Project?,
                           module: Module?,
                           existingSdks: List<Sdk>,
                           newProjectPath: String?,
                           context: UserDataHolder): PyAddNewEnvPanel {
    return PyAddPipEnvPanel(null, null, existingSdks, newProjectPath, context)
  }
}