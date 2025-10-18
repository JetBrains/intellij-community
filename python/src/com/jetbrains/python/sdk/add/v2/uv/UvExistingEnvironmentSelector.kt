// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.add.v2.CustomExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.DetectedSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PathValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.add.v2.Version
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.isAssociatedWithModule
import com.jetbrains.python.sdk.uv.isUv
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.statistics.version
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.jetbrains.python.venvReader.tryResolvePath
import java.nio.file.Path
import kotlin.io.path.pathString

internal class UvExistingEnvironmentSelector<P: PathHolder>(model: PythonMutableTargetAddInterpreterModel<P>, module: Module?)
  : CustomExistingEnvironmentSelector<P>("uv", model, module) {
  override val toolState: PathValidator<Version, P, ValidatedPath.Executable<P>> = model.uvState.toolValidator
  override val interpreterType: InterpreterType = InterpreterType.UV

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val sdkHomePath = selectedEnv.get()?.homePath
    val selectedInterpreterPath = sdkHomePath as? PathHolder.Eel
                                  ?: return PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", sdkHomePath))
    val allSdk = PythonSdkUtil.getAllSdks()
    val existingSdk = allSdk.find { it.homePath == selectedInterpreterPath.path.pathString }
    val associatedModule = extractModule(moduleOrProject)
    val basePathString = associatedModule?.basePath ?: moduleOrProject.project.basePath
    val projectDir = tryResolvePath(basePathString)
                     ?: return PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", basePathString))

    // uv sdk in current module
    if (existingSdk != null && existingSdk.isUv && existingSdk.isAssociatedWithModule(associatedModule)) {
      return Result.success(existingSdk)
    }

    val workingDirectory =
      VirtualEnvReader().getVenvRootPath(selectedInterpreterPath.path)
      ?: tryResolvePath(existingSdk?.associatedModulePath)
      ?: projectDir

    return setupExistingEnvAndSdk(
      envExecutable = selectedInterpreterPath.path,
      envWorkingDir = workingDirectory,
      usePip = existingSdk?.isUv == true,
      projectDir = projectDir,
      existingSdks = allSdk.toList()
    )
  }

  override suspend fun detectEnvironments(modulePath: Path): List<DetectedSelectableInterpreter<P>> {
    val existingEnvs = PythonSdkUtil.getAllSdks().filter {
      it.isUv && (it.associatedModulePath == modulePath.pathString || it.associatedModulePath == null)
    }.mapNotNull { env ->
      env.homePath?.let { path ->
        model.fileSystem.parsePath(path).successOrNull?.let { homePath ->
          DetectedSelectableInterpreter(homePath, env.version, false)
        }
      }
    }
    return existingEnvs
  }

  private fun extractModule(moduleOrProject: ModuleOrProject): Module? =
    when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
      else -> null
    }
}