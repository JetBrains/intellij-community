// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.text.nullize
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.asPythonResult
import com.jetbrains.python.sdk.pipenv.pipEnvPath
import com.jetbrains.python.sdk.pipenv.setupPipEnvSdkUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import java.nio.file.Path
import kotlin.io.path.pathString

internal class EnvironmentCreatorPip(model: PythonMutableTargetAddInterpreterModel) : CustomNewEnvironmentCreator("pipenv", model) {
  override val interpreterType: InterpreterType = InterpreterType.PIPENV
  override val executable: ObservableMutableProperty<String> = model.state.pipenvExecutable
  override val installationVersion: String? = null

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path?.pathString ?: executable.get().nullize() ?: return
    PropertiesComponent.getInstance().pipEnvPath = savingPath
  }

  override suspend fun setupEnvSdk(project: Project, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): PyResult<Sdk> =
    setupPipEnvSdkUnderProgress(project, module, baseSdks, projectPath, homePath, installPackages).asPythonResult()

  override suspend fun detectExecutable() {
    model.detectPipEnvExecutable()
  }
}