// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.community.impl.uv.common.UV_UI_INFO
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.CustomExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.DetectedSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.add.v2.savePathForEelOnly
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.isAssociatedWithModule
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.uv.impl.setUvExecutable
import com.jetbrains.python.sdk.uv.isUv
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.jetbrains.python.venvReader.tryResolvePath
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.pathString


internal class UvExistingEnvironmentSelector<P : PathHolder>(model: PythonMutableTargetAddInterpreterModel<P>, module: Module?) :
  CustomExistingEnvironmentSelector<P>("uv", model, module) {
  override val interpreterType: InterpreterType = InterpreterType.UV
  override val toolState: ToolValidator<P> = model.uvViewModel.toolValidator
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?> = model.uvViewModel.uvExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { pathHolder ->
    savePathForEelOnly(pathHolder) { path -> setUvExecutable(path) }
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val sdkHomePath = selectedEnv.get()?.homePath
    val selectedInterpreterPath = sdkHomePath as? PathHolder.Eel
                                  ?: return PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", sdkHomePath))
    val allSdk = PythonSdkUtil.getAllSdks()
    val existingSdk = allSdk.find { it.homePath == selectedInterpreterPath.path.pathString }
    val associatedModule = extractModule(moduleOrProject)
    val basePathString = associatedModule?.baseDir?.path ?: moduleOrProject.project.basePath
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
      moduleDir = projectDir,
    )
  }

  override suspend fun detectEnvironments(modulePath: Path): List<DetectedSelectableInterpreter<P>> {
    val rootFolders = Files.walk(modulePath, 1)
      .filter(Files::isDirectory)
      .collect(Collectors.toList())

    val existingEnvs = rootFolders.mapNotNull { possibleVenvHome ->
      val pythonBinaryPath = possibleVenvHome.resolvePythonBinary()?.takeIf { it.exists() }
                             ?: return@mapNotNull null
      val pythonInfo = pythonBinaryPath.validatePythonAndGetInfo().getOrNull()
                       ?: return@mapNotNull null

      val pathHolder = PathHolder.Eel(pythonBinaryPath) as P
      DetectedSelectableInterpreter(pathHolder, pythonInfo, false, UV_UI_INFO)
    }
    return existingEnvs
  }

  private fun extractModule(moduleOrProject: ModuleOrProject): Module? =
    when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
      else -> null
    }
}
