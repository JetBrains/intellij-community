// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.errorProcessing.asKotlinResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.*
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import java.nio.file.Path

internal class UvPackageManager(project: Project, sdk: Sdk, private val uv: UvLowLevel) : PythonPackageManager(project, sdk) {
  override var installedPackages: List<PythonPackage> = emptyList()
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager(project)


  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): Result<Unit> {
    val result = if (sdk.uvUsePackageManagement) {
      uv.installPackage(installRequest, emptyList())
    }
    else {
      uv.addDependency(installRequest, emptyList())
    }.asKotlinResult()

    result.getOrElse {
      return Result.failure(it)
    }

    return Result.success(Unit)
  }

  override suspend fun updatePackageCommand(specification: PythonRepositoryPackageSpecification): Result<Unit> {
    installPackageCommand(specification.toInstallRequest(), emptyList()).getOrElse {
      return Result.failure(it)
    }

    return Result.success(Unit)
  }

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<Unit> {
    val result = if (sdk.uvUsePackageManagement) {
      uv.uninstallPackage(pkg)
    }
    else {
      uv.removeDependency(pkg)
    }.asKotlinResult()

    result.getOrElse {
      return Result.failure(it)
    }

    return Result.success(Unit)
  }

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> {
    return uv.listPackages().asKotlinResult()
  }

  override suspend fun loadOutdatedPackagesCommand(): Result<List<PythonOutdatedPackage>> {
    return uv.listOutdatedPackages().asKotlinResult()
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