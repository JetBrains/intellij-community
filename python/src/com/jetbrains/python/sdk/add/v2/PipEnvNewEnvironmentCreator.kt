// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.text.nullize
import com.jetbrains.python.sdk.pipenv.pipEnvPath
import com.jetbrains.python.sdk.pipenv.setupPipEnvSdkUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import java.nio.file.Path


class PipEnvNewEnvironmentCreator(model: PythonMutableTargetAddInterpreterModel) : CustomNewEnvironmentCreator("pipenv", model) {
  override val interpreterType: InterpreterType = InterpreterType.PIPENV
  override val executable: ObservableMutableProperty<String> = model.state.pipenvExecutable
  override val installationScript: Path? = null

  override fun savePathToExecutableToProperties() {
    PropertiesComponent.getInstance().pipEnvPath = executable.get().nullize()
  }

  override suspend fun setupEnvSdk(project: Project?, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): Result<Sdk> =
    setupPipEnvSdkUnderProgress(project, module, baseSdks, projectPath, homePath, installPackages)

  override suspend fun detectExecutable() {
    model.detectPipEnvExecutable()
  }
}