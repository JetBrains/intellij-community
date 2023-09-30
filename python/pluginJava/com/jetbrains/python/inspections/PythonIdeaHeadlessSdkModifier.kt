// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.headless.PythonHeadlessSdkModifier

class PythonIdeaHeadlessSdkModifier : PythonHeadlessSdkModifier {

  override fun setSdk(project: Project, sdk: Sdk): Boolean {
    PythonPluginCommandLineInspectionProjectConfigurator.configurePythonSdkForAllPythonModules(project, sdk, null)
    return true
  }
}