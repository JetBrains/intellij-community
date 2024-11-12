// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import java.util.regex.Pattern
import org.jetbrains.annotations.TestOnly

class PoetryPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  @Volatile
  override var installedPackages: List<PythonPackage> = emptyList()
  override val repositoryManager: PipRepositoryManager = PipRepositoryManager(project, sdk)

  @Volatile
  private var outdatedPackages: Map<String, PoetryOutdatedVersion> = emptyMap()

  override suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<String> =
    poetryInstallPackage(sdk, specification.getVersionForPoetry(), options)

  override suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<String> =
    poetryInstallPackage(sdk, specification.getVersionForPoetry(), emptyList())

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<String> = poetryUninstallPackage(sdk, pkg.name)

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> {
    val output = poetryReloadPackages(sdk).getOrElse { return Result.failure(it) }
    return Result.success(parsePoetryShow(output))
  }

  override suspend fun reloadPackages(): Result<List<PythonPackage>> {
    updateOutdatedPackages()
    return super.reloadPackages()
  }

  internal fun getOutdatedPackages(): Map<String, PoetryOutdatedVersion> = outdatedPackages

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
private fun parsePoetryShow(input: String): List<PythonPackage> {
  val result = mutableListOf<PythonPackage>()
  input.split("\n").forEach { line ->
    if (line.isNotBlank()) {
      val packageInfo = line.trim().split(" ").map { it.trim() }.filter { it.isNotBlank() }
      result.add(PythonPackage(packageInfo[0], packageInfo[1], false))
    }
  }
  return result
}

/**
 * Parses the output of `poetry show --outdated` into a list of packages.
 */
private fun parsePoetryShowOutdated(input: String): Map<String, PoetryOutdatedVersion> =
  input
    .lines()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .mapNotNull { line ->
      line.split(Pattern.compile(" +"))
        .takeIf { it.size > 3 }?.let { it[0] to PoetryOutdatedVersion(it[1], it[2]) }
    }.toMap()

@TestOnly
fun parsePoetryShowTest(input: String): List<PythonPackage> = parsePoetryShow(input)

@TestOnly
fun parsePoetryShowOutdatedTest(input: String): Map<String, PoetryOutdatedVersion> = parsePoetryShowOutdated(input)