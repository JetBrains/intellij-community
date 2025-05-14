// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager

internal class CompositePythonPackageManager(
  project: Project,
  sdk: Sdk,
  private val managers: List<PythonPackageManager>,
) : PythonPackageManager(project, sdk) {

  @Volatile
  override var installedPackages: List<PythonPackage> = emptyList()

  override var repositoryManager: PythonRepositoryManager =
    CompositePythonRepositoryManager(project, managers.map { it.repositoryManager })

  private val managerNames = managers.joinToString { it.javaClass.simpleName }

  override suspend fun loadOutdatedPackagesCommand(): Result<List<PythonOutdatedPackage>> {
    val results = mutableListOf<PythonOutdatedPackage>()
    val exceptions = mutableListOf<Throwable>()

    for (manager in managers) {
      manager.loadOutdatedPackagesCommand()
        .onSuccess { results.addAll(it) }
        .onFailure { exceptions.add(it) }
    }

    return if (results.isNotEmpty()) {
      Result.success(results)
    }
    else {
      Result.failure(createCompositeException(
        exceptions,
        PyBundle.message("python.packaging.composite.list.outdated.packages.error", managerNames)
      ))
    }
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): Result<Unit> {
    return processPackageOperation(
      errorMessageKey = "python.packaging.composite.install.package.error",
      operation = { it.installPackageCommand(installRequest, options) },
      name = installRequest.title
    )
  }

  override suspend fun updatePackageCommand(specification: PythonRepositoryPackageSpecification): Result<Unit> {
    return processPackageOperation(
      errorMessageKey = "python.packaging.composite.update.package.error",
      operation = { it.updatePackage(specification) },
      name = specification.name
    )
  }

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<Unit> {
    return processPackageOperation(
      errorMessageKey = "python.packaging.composite.uninstall.package.error",
      operation = { it.uninstallPackage(pkg) },
      name = pkg.name
    )
  }

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> {
    val results = mutableListOf<PythonPackage>()
    val exceptions = mutableListOf<Throwable>()

    for (manager in managers) {
      manager.reloadPackages()
        .onSuccess { results.addAll(it) }
        .onFailure { exceptions.add(it) }
    }

    return if (results.isNotEmpty()) {
      Result.success(results)
    }
    else {
      Result.failure(createCompositeException(
        exceptions,
        PyBundle.message("python.packaging.composite.reload.packages.error", managerNames)
      ))
    }
  }

  private suspend fun processPackageOperation(
    errorMessageKey: String,
    operation: suspend (PythonPackageManager) -> Result<*>,
    name: String,
  ): Result<Unit> {
    val exceptions = mutableListOf<Throwable>()
    for (manager in managers) {
      operation(manager)
        .onSuccess { return Result.success(Unit) }
        .onFailure { exceptions.add(it) }
    }

    return Result.failure(createCompositeException(
      exceptions,
      PyBundle.message(errorMessageKey, name, managerNames)
    ))
  }

  fun createCompositeException(
    exceptions: List<Throwable>,
    defaultMessage: String,
  ): RuntimeException {
    if (exceptions.isEmpty()) {
      return RuntimeException(defaultMessage)
    }

    val concatenatedMessages = exceptions.joinToString(separator = "; ") { exception ->
      exception.message ?: exception.toString()
    }

    return RuntimeException(concatenatedMessages)
  }
}