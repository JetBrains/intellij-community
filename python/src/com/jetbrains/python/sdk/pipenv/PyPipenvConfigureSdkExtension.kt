// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PyConfigureSdkExtension

class PyPipenvConfigureSdkExtension : PyConfigureSdkExtension {

  override val progressText: String
    get() = PyBundle.message("looking.for.pipfile")

  override fun configureSdk(project: Project, module: Module, existingSdks: List<Sdk>): Sdk? {
    return detectAndSetupPipEnv(project, module, existingSdks)
  }
}