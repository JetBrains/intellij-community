// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.packaging

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.HatchService
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.util.cancelOnDispose
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.hatch.sdk.createHatchServiceAsync
import com.jetbrains.python.hatch.sdk.isHatch
import com.jetbrains.python.packaging.management.PythonManagerCliSpec
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.pip.PipPythonPackageManager
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.add.v2.toFileSystem
import kotlinx.coroutines.Deferred
import java.nio.file.Path

internal class HatchPackageManager(
  project: Project,
  sdk: Sdk,
  hatchServiceDeferred: Deferred<PyResult<HatchService<*>>>,
) : PipPythonPackageManager(project, sdk) {
  override val cliSpecs: List<PythonManagerCliSpec> = listOf(
    PythonManagerCliSpec("hatch", { HatchConfiguration.getOrDetectHatchExecutablePath(localEel.toFileSystem()).successOrNull?.path }),
    PythonManagerCliSpec("pip", { sdk.homePath?.let { Path.of(it) } }, runAsModule = true),
  )

  private lateinit var hatchService: PyResult<HatchService<*>>
  private val hatchServiceDeferred = hatchServiceDeferred.also { it.cancelOnDispose(this) }

  private suspend fun <T> withHatch(action: suspend (HatchService<*>) -> PyResult<T>): PyResult<T> {
    if (!this::hatchService.isInitialized) {
      hatchService = hatchServiceDeferred.await()
    }

    return when (val hatchServiceResult = hatchService) {
      is Result.Success -> action(hatchServiceResult.result)
      is Result.Failure -> hatchServiceResult
    }
  }

  override suspend fun syncLockedCommand(): PyResult<Unit> {
    return withHatch { hatch -> hatch.syncDependencies().mapSuccess { } }
  }

  override val dependenciesFilesRelativePaths: List<Path>
    get() = listOf(
      Path.of(PY_PROJECT_TOML),
    )
}

internal class HatchPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? {
    if (!sdk.isHatch) {
      return null
    }

    val hatchService = sdk.createHatchServiceAsync(PyPackageCoroutine.getScope(project)) ?: return null
    return HatchPackageManager(project, sdk, hatchService)
  }
}
