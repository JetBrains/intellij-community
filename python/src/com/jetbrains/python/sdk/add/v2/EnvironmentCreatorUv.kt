// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.text.nullize
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.uv.impl.setUvExecutable
import com.jetbrains.python.sdk.uv.setupUvSdkUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import java.nio.file.Path

internal class EnvironmentCreatorUv(model: PythonMutableTargetAddInterpreterModel) : CustomNewEnvironmentCreator("uv", model) {
  override val interpreterType: InterpreterType = InterpreterType.UV
  override val executable: ObservableMutableProperty<String> = model.state.uvExecutable
  override val installationVersion: String? = null

  override fun onShown() {
    // FIXME: validate base interpreters against pyprojecttoml version. See poetry
    basePythonComboBox.setItems(model.baseInterpreters)
  }

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path ?: executable.get().nullize()?.let { Path.of(it) } ?: return
    setUvExecutable(savingPath)
  }

  override suspend fun setupEnvSdk(project: Project?, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): Result<Sdk> {
    if (module == null) {
      // FIXME: should not happen, proper error
      return Result.failure(Exception("module is null"))
    }

    val python = homePath?.let { Path.of(it) }
    return setupUvSdkUnderProgress(ModuleOrProject.ModuleAndProject(module), baseSdks, python)
  }

  override suspend fun detectExecutable() {
    model.detectUvExecutable()
  }
}