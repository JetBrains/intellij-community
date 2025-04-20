// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class PoetryPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  @Volatile
  override var installedPackages: List<PythonPackage> = emptyList()
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager(project)

  @Volatile
  private var outdatedPackages: Map<String, PythonOutdatedPackage> = emptyMap()

  override suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<Unit> {
    poetryInstallPackage(sdk, specification.getVersionForPoetry(), options)
      .onFailure { return Result.failure(it) }

    return Result.success(Unit)
  }

  override suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<Unit> {
    poetryInstallPackage(sdk, specification.getVersionForPoetry(), emptyList())
      .onFailure { return Result.failure(it) }

    return Result.success(Unit)
  }

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<Unit> {
    poetryUninstallPackage(sdk, pkg.name)
      .onFailure { return Result.failure(it) }

    return Result.success(Unit)
  }

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> {
    val (installed, _) = poetryListPackages(sdk).getOrElse {
      return Result.failure(it)
    }

    outdatedPackages = poetryShowOutdated(sdk).getOrElse {
      emptyMap()
    }

    val packages = installed.map {
      PythonPackage(it.name, it.version, false)
    }

    return Result.success(packages)
  }

  internal fun getOutdatedPackages(): Map<String, PythonOutdatedPackage> = outdatedPackages

  private fun PythonPackageSpecification.getVersionForPoetry(): String = if (versionSpecs == null) name else "$name@$versionSpecs"
}

/**
 * Parses the output of `poetry show` into a list of packages.
 */

@TestOnly
fun parsePoetryShowOutdatedTest(input: String): Map<String, PythonOutdatedPackage> {
  return parsePoetryShowOutdated(input)
}