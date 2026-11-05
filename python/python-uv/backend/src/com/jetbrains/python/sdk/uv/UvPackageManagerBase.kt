// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.uv.backend.UvPyTool
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonManagerCliSpec
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.add.v2.toFileSystem
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

/**
 * What the two uv managers share: the uv CLI of the SDK and the commands that read the environment.
 *
 * [UvPackageManager] serves [UvMode.Project]. [UvPipPackageManager] serves [UvMode.Pip]. Both list the installed and
 * the outdated packages with `uv pip list`, and both update a package by installing it again.
 */
@ApiStatus.Internal
abstract class UvPackageManagerBase(
  project: Project,
  interpreter: PythonInterpreter,
  uvExecutionContextDeferred: Deferred<UvExecutionContext<*>>,
) : PythonPackageManager(project, interpreter) {
  override val installedPackagesIncludeTransitive: Boolean = true
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)
  override fun getCliSpecs(eelApi: EelApi): List<PythonManagerCliSpec> = listOf(
    PythonManagerCliSpec("uv", { eelApi.toFileSystem().resolveExecutable(UvPyTool.getInstance())?.path })
  )

  protected val uvExecutionContextDeferred: Deferred<UvExecutionContext<*>> = uvExecutionContextDeferred.cancelWithManager()
  private lateinit var uvLowLevel: PyResult<UvLowLevel<*>>

  /** Runs [action] with the uv CLI of this SDK. The first call creates the CLI, and every later call reuses it. */
  protected suspend fun <T> withUv(action: suspend (UvLowLevel<*>) -> PyResult<T>): PyResult<T> {
    if (!this::uvLowLevel.isInitialized) {
      uvLowLevel = uvExecutionContextDeferred.await().createUvCli()
    }

    return when (val uvResult = uvLowLevel) {
      is Result.Success -> action(uvResult.result)
      is Result.Failure -> uvResult
    }
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specifications.toList())
    return installPackageCommand(request, emptyList())
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> = withUv { uv -> uv.listPackages() }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> = withUv { uv -> uv.listOutdatedPackages() }
}

/** Picks the manager for the [UvMode] of the SDK. It pins a legacy SDK first. */
internal class UvPackageManagerProvider : PythonPackageManagerProvider {
  // The legacy mode pin reads and writes the SDK data.
  @Suppress("DEPRECATION")
  override fun createPackageManager(project: Project, interpreter: PythonInterpreter): PythonPackageManager? {
    if (!interpreter.isUv) {
      return null
    }
    val mode = pinLegacyUvMode(project, interpreter.getSdkAPI())
    val uvExecutionContext = interpreter.getUvExecutionContextAsync(PyPackageCoroutine.getScope(project), project) ?: return null
    return when (mode) {
      UvMode.Project -> UvPackageManager(project, interpreter, uvExecutionContext)
      is UvMode.Pip -> UvPipPackageManager(project, interpreter, uvExecutionContext)
    }
  }
}
