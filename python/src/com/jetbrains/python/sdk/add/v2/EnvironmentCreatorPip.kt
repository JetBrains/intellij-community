// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.pipenv.getPipEnvExecutable
import com.jetbrains.python.sdk.pipenv.pipEnvPath
import com.jetbrains.python.sdk.pipenv.setupPipEnvSdkWithProgressReport
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

class PipenvState<P : PathHolder>(
  fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
) : ToolState {
  val pipenvExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    toolVersionPrefix = "pipenv",
    backProperty = pipenvExecutable,
    propertyGraph = propertyGraph,
    defaultPathSupplier = {
      when (fileSystem) {
        is FileSystem.Eel -> {
          if (fileSystem.eelApi == localEel) getPipEnvExecutable().getOrNull()?.let { PathHolder.Eel(it) } as P?
          else null // getPipEnvExecutable() works only with localEel currently
        }
        else -> null
      }
    }
  )

  override fun initialize(scope: CoroutineScope) {
    toolValidator.initialize(scope)
  }
}

internal class EnvironmentCreatorPip<P : PathHolder>(model: PythonMutableTargetAddInterpreterModel<P>, errorSink: ErrorSink) : CustomNewEnvironmentCreator<P>("pipenv", model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.PIPENV
  override val toolValidator: ToolValidator<P> = model.pipenvState.toolValidator

  override suspend fun savePathToExecutableToProperties(pathHolder: PathHolder?) {
    if ((model.fileSystem as? FileSystem.Eel)?.eelApi !is LocalEelApi) return

    val savingPath = (pathHolder as? PathHolder.Eel)?.path
                     ?: (toolValidator.backProperty.get()?.pathHolder as? PathHolder.Eel)?.path
    savingPath?.let {
      PropertiesComponent.getInstance().pipEnvPath = it.toString()
    }
  }

  override suspend fun setupEnvSdk(moduleBasePath: Path, baseSdks: List<Sdk>, basePythonBinaryPath: P?, installPackages: Boolean): PyResult<Sdk> {
    return when (basePythonBinaryPath) {
      is PathHolder.Eel -> setupPipEnvSdkWithProgressReport(moduleBasePath, baseSdks, basePythonBinaryPath.path, installPackages)
      else -> PyResult.localizedError(PyBundle.message("target.is.not.supported", basePythonBinaryPath))
    }
  }
}