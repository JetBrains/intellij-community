// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import java.nio.file.Path

internal class UvPackageManager(project: Project, sdk: Sdk, private val uv: UvLowLevel) : PythonPackageManager(project, sdk) {
  override var installedPackages: List<PythonPackage> = emptyList()
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager(project, sdk)

  @Volatile
  var outdatedPackages: Map<String, PythonOutdatedPackage> = emptyMap()

  override suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<Unit> {
    val result = if (sdk.uvUsePackageManagement) {
      uv.installPackage(specification, emptyList())
    }
    else {
      uv.addDependency(specification, emptyList())
    }

    result.getOrElse {
      return Result.failure(it)
    }

    return Result.success(Unit)
  }

  override suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<Unit> {
    installPackageCommand(specification, emptyList()).getOrElse {
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
    }

    result.getOrElse {
      return Result.failure(it)
    }

    return Result.success(Unit)
  }

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> {
    // ignoring errors as handling outdated packages is a pretty new option
    uv.listOutdatedPackages().onSuccess {
      outdatedPackages = it.associateBy { it.name }
    }

    return uv.listPackages()
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