// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager

class CompositePythonPackageManager(
  project: Project,
  sdk: Sdk,
  private val managers: List<PythonPackageManager>,
) : PythonPackageManager(project, sdk) {
  @Volatile
  override var installedPackages: List<PythonPackage> = emptyList()

  // TODO: composite one
  override var repositoryManager: PythonRepositoryManager = managers.first().repositoryManager

  private fun isInRepository(repositoryManager: PythonRepositoryManager, pkgName: String) =
    repositoryManager.allPackages().contains(pkgName)

  override suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<Unit> {
    val exceptionList = mutableListOf<Throwable>()
    for (manager in managers) {
      repositoryManager = manager.repositoryManager
      installedPackages = manager.installedPackages

      if (!isInRepository(repositoryManager, specification.name)) continue
      val executionResult = manager.installPackage(specification, options)
      executionResult
        .onSuccess { return Result.success(Unit) }
        .onFailure { exceptionList.add(it) }
    }

    return Result.failure(exceptionList.lastOrNull() ?: RuntimeException("No package managers found for package $specification"))
  }

  override suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<Unit> {
    val exceptionList = mutableListOf<Throwable>()

    for (manager in managers) {
      repositoryManager = manager.repositoryManager
      installedPackages = manager.installedPackages

      if (!isInRepository(repositoryManager, specification.name)) continue
      val executionResult = manager.updatePackage(specification)
      executionResult
        .onSuccess { return Result.success(Unit) }
        .onFailure { exceptionList.add(it) }
    }

    return Result.failure(exceptionList.lastOrNull() ?: RuntimeException("No package managers found for package $specification"))
  }

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<Unit> {
    val exceptionList = mutableListOf<Throwable>()

    for (manager in managers) {
      repositoryManager = manager.repositoryManager
      installedPackages = manager.installedPackages

      if (!isInRepository(repositoryManager, pkg.name)) {
        continue
      }

      val executionResult = manager.uninstallPackage(pkg)
      executionResult
        .onSuccess { return Result.success(Unit) }
        .onFailure { exceptionList.add(it) }
    }

    return Result.failure(exceptionList.lastOrNull() ?: RuntimeException("No package managers found for package $pkg"))
  }

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> {
    val exceptionList = mutableListOf<Throwable>()

    for (manager in managers) {
      repositoryManager = manager.repositoryManager
      installedPackages = manager.installedPackages

      manager.reloadPackages()
        .onSuccess { return Result.success(it) }
        .onFailure { exceptionList.add(it) }
    }

    return Result.failure(exceptionList.lastOrNull() ?: RuntimeException("No package managers found"))
  }
}