// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import java.nio.file.Path

internal class UvPackageManager(project: Project, sdk: Sdk, private val uv: UvLowLevel) : PythonPackageManager(project, sdk) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager(project)

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    val result = if (sdk.uvUsePackageManagement) {
      uv.installPackage(installRequest, emptyList())
    }
    else {
      uv.addDependency(installRequest, emptyList())
    }
    return result
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val specsWithoutVersion = specifications.map { it.copy(versionSpec = null) }
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specsWithoutVersion)
    val result = installPackageCommand(request, emptyList())

    return result
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
    val result = if (sdk.uvUsePackageManagement) {
      uv.uninstallPackages(pythonPackages)
    }
    else {
      uv.removeDependencies(pythonPackages)
    }
    return result
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    return uv.listPackages()
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    return uv.listOutdatedPackages()
  }

  suspend fun sync(): PyExecResult<String> {
    return uv.sync()
  }

  suspend fun lock(): PyExecResult<String> {
    return uv.lock()
  }
}

class UvPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? {
    if (!sdk.isUv) {
      return null
    }

    val uvWorkingDirectory = (sdk.sdkAdditionalData as UvSdkAdditionalData).uvWorkingDirectory ?: Path.of(project.basePath!!)
    val uv = createUvLowLevel(uvWorkingDirectory, createUvCli())
    return UvPackageManager(project, sdk, uv)
  }
}