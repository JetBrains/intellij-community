// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.runPackagingTool
import org.jetbrains.annotations.ApiStatus
import kotlin.collections.plus

@ApiStatus.Experimental
open class PipPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  @Volatile
  override var installedPackages: List<PythonPackage> = emptyList()
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager(project, sdk)

  override suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<String> =
    try {
      Result.success(runPackagingTool("install", specification.buildInstallationString() + options, PyBundle.message("python.packaging.install.progress", specification.name)))
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
    }

  override suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<String> =
    try {
      Result.success(runPackagingTool("install", listOf("--upgrade") + specification.buildInstallationString(), PyBundle.message("python.packaging.update.progress", specification.name)))
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
    }

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<String> =
    try {
      Result.success(runPackagingTool("uninstall", listOf(pkg.name), PyBundle.message("python.packaging.uninstall.progress", pkg.name)))
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
    }

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> =
    try {
      val output = runPackagingTool("list", emptyList(), PyBundle.message("python.packaging.list.progress"))
      val packages = output.lineSequence()
        .filter { it.isNotBlank() }
        .map {
          val line = it.split("\t")
          PythonPackage(line[0], line[1], isEditableMode = false)
        }
        .sortedWith(compareBy(PythonPackage::name))
        .toList()
      Result.success(packages)
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
    }
}