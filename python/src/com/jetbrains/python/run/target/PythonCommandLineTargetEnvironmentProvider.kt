// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.target

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.python.run.PythonExecution
import com.jetbrains.python.run.PythonRunParams
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface PythonCommandLineTargetEnvironmentProvider {
  fun extendTargetEnvironment(project: Project,
                              helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest,
                              pythonExecution: PythonExecution,
                              runParams: PythonRunParams)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PythonCommandLineTargetEnvironmentProvider> =
      ExtensionPointName.create("Pythonid.pythonCommandLineTargetEnvironmentProvider")
  }
}