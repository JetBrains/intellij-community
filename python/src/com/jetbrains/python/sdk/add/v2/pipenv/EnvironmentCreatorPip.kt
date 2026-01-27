// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.pipenv

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.impl.pipenv.pipenvPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.pipenv.setupPipEnvSdkWithProgressReport
import com.jetbrains.python.statistics.InterpreterType
import java.nio.file.Path

internal class EnvironmentCreatorPip<P : PathHolder>(model: PythonMutableTargetAddInterpreterModel<P>, errorSink: ErrorSink) : CustomNewEnvironmentCreator<P>("pipenv", model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.PIPENV
  override val toolValidator: ToolValidator<P> = model.pipenvViewModel.toolValidator
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?> = model.pipenvViewModel.pipenvExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { pathHolder ->
    savePathForEelOnly(pathHolder) { path -> PropertiesComponent.getInstance().pipenvPath = path.toString() }
  }

  override suspend fun setupEnvSdk(moduleBasePath: Path): PyResult<Sdk> {
    val basePythonBinaryPath = model.getOrInstallBasePython()

    return when (basePythonBinaryPath) {
      is PathHolder.Eel -> setupPipEnvSdkWithProgressReport(
        moduleBasePath = moduleBasePath,
        basePythonBinaryPath = basePythonBinaryPath.path,
        installPackages = false
      )
      else -> PyResult.localizedError(PyBundle.message("target.is.not.supported", basePythonBinaryPath))
    }
  }
}
