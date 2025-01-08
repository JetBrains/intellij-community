// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import org.jetbrains.annotations.TestOnly

class PoetryPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  @Volatile
  override var installedPackages: List<PythonPackage> = emptyList()
  override val repositoryManager: PipRepositoryManager = PipRepositoryManager(project, sdk)

  @Volatile
  private var outdatedPackages: Map<String, PythonOutdatedPackage> = emptyMap()

  override suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<String> =
    poetryInstallPackage(sdk, specification.getVersionForPoetry(), options)

  override suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<String> =
    poetryInstallPackage(sdk, specification.getVersionForPoetry(), emptyList())

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<String> = poetryUninstallPackage(sdk, pkg.name)

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> {
    return poetryShowPackages(sdk)
  }

  override suspend fun reloadPackages(): Result<List<PythonPackage>> {
    updateOutdatedPackages()
    return super.reloadPackages()
  }

  internal fun getOutdatedPackages(): Map<String, PythonOutdatedPackage> = outdatedPackages

  /**
   * Updates the list of outdated packages by running the Poetry command
   * `poetry show --outdated`, parsing its output, and storing the results.
   */
  private suspend fun updateOutdatedPackages() {
    val outputOutdatedPackages = runPoetryWithSdk(sdk, "show", "--outdated").getOrElse {
      outdatedPackages = emptyMap()
      return
    }

    outdatedPackages = parsePoetryShowOutdated(outputOutdatedPackages)
  }

  private fun PythonPackageSpecification.getVersionForPoetry(): String = if (versionSpecs == null) name else "$name@$versionSpecs"
}

/**
 * Parses the output of `poetry show` into a list of packages.
 */

@TestOnly
fun parsePoetryShowOutdatedTest(input: String): Map<String, PythonOutdatedPackage> {
  return parsePoetryShowOutdated(input)
}