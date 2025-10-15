// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.LocalEelApi
import com.intellij.python.community.impl.pipenv.pipenvPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.pipenv.setupPipEnvSdkWithProgressReport
import com.jetbrains.python.statistics.InterpreterType
import java.nio.file.Path

internal class EnvironmentCreatorPip<P : PathHolder>(model: PythonMutableTargetAddInterpreterModel<P>, errorSink: ErrorSink) : CustomNewEnvironmentCreator<P>("pipenv", model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.PIPENV
  override val executable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = model.state.pipenvExecutable

  override suspend fun savePathToExecutableToProperties(pathHolder: PathHolder?) {
    if ((model.fileSystem as? FileSystem.Eel)?.eelApi !is LocalEelApi) return

    val savingPath = (pathHolder as? PathHolder.Eel)?.path
                     ?: (executable.get()?.pathHolder as? PathHolder.Eel)?.path
    savingPath?.let {
      PropertiesComponent.getInstance().pipenvPath = it.toString()
    }
  }

  override suspend fun setupEnvSdk(moduleBasePath: Path, baseSdks: List<Sdk>, basePythonBinaryPath: P?, installPackages: Boolean): PyResult<Sdk> {
    return when (basePythonBinaryPath) {
      is PathHolder.Eel -> setupPipEnvSdkWithProgressReport(moduleBasePath, baseSdks, basePythonBinaryPath.path, installPackages)
      else -> PyResult.localizedError(PyBundle.message("target.is.not.supported", basePythonBinaryPath))
    }
  }

  override suspend fun detectExecutable() {
    model.detectPipEnvExecutable()
  }
}
