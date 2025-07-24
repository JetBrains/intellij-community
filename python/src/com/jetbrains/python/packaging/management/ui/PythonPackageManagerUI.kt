// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.util.ShowingMessageErrorSync
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * A class responsible for managing Python packages within a specific SDK and project.
 * Provides functionality for reloading, installing, updating, and uninstalling Python packages.
 *
 * This class is used for work with packages with the process and handling all execution errors and providing background process
 * All functions return the calculated result or null if the operation was failed.
 * Prefer this class for external usage
 *
 * @see PythonPackageManager if you need more control over the process and handling errors
 *
 * @constructor Creates an instance of the PythonPackageManagerUI class.
 * @param project The project within which the operations are performed.
 * @param sdk The Python SDK associated with the operations.
 * @param sink The error sink used for reporting errors during operations.
 */
@ApiStatus.Internal
class PythonPackageManagerUI(val manager: PythonPackageManager, val sink: ErrorSink = ShowingMessageErrorSync) {
  val project: Project = manager.project
  val sdk: Sdk = manager.sdk

  /**
   * @return List of installed packages or null if the operation was failed.
   */
  suspend fun reloadPackagesBackground(): List<PythonPackage>? {
    return executeCommand(PyBundle.message("python.packaging.list.packages")) {
      manager.reloadPackages()
    }
  }

  /**
   * @return List of all installed packages or null if the operation was failed.
   */
  suspend fun installPackagesRequestBackground(
    installRequest: PythonPackageInstallRequest,
    options: List<String> = emptyList(),
  ): List<PythonPackage>? {
    val progressTitle = when (installRequest) {
      is PythonPackageInstallRequest.ByLocation -> PyBundle.message("python.packaging.installing.package", installRequest.title)
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> if (installRequest.specifications.size == 1) {
        PyBundle.message("python.packaging.installing.package", installRequest.specifications.first().name)
      }
      else {
        PyBundle.message("python.packaging.installing.packages")
      }
    }

    return executeCommand(progressTitle) {
      manager.installPackage(installRequest, options)
    }
  }

  /**
   * @return List of all installed packages or null if the operation was failed.
   */
  suspend fun updatePackagesBackground(
    packages: List<PythonRepositoryPackageSpecification>,
  ): List<PythonPackage>? {
    val progressTitle = if (packages.size > 1) {
      PyBundle.message("python.packaging.updating.packages")
    }
    else {
      PyBundle.message("python.packaging.updating.package", packages.first().name)
    }

    return executeCommand(progressTitle) {
      manager.updatePackages(*packages.toTypedArray())
    }
  }

  /**
   * @return List of all installed packages or null if the operation was failed.
   */
  suspend fun uninstallPackagesBackground(
    packages: List<String>,
  ): List<PythonPackage>? {
    val progressTitle = if (packages.size > 1) {
      PyBundle.message("python.packaging.uninstall.packages")
    }
    else {
      PyBundle.message("python.packaging.uninstall.package", packages.first())
    }

    return executeCommand(progressTitle
    ) {
      manager.uninstallPackage(*packages.toTypedArray())
    }
  }

  suspend fun syncBackground() {
    val progressTitle = PyBundle.message("python.packaging.sync.packages")
    executeCommand(progressTitle) {
      manager.sync()
    }
  }

  @ApiStatus.Internal
  suspend fun <T> executeCommand(
    progressTitle: @Nls String,
    operation: suspend (() -> PyResult<T>?),
  ): T? = PythonPackageManagerUIHelpers.runPackagingOperationMaybeBackground(manager.project, sink, progressTitle) {

  operation()
  }

  companion object {
    @ApiStatus.Internal
    fun forSdk(project: Project, sdk: Sdk, sink: ErrorSink = ShowingMessageErrorSync): PythonPackageManagerUI {
      val packageManager = PythonPackageManager.forSdk(project, sdk)
      return forPackageManager(packageManager, sink)
    }

    @ApiStatus.Internal
    fun forPackageManager(
      packageManager: PythonPackageManager,
      sink: ErrorSink = ShowingMessageErrorSync,
    ): PythonPackageManagerUI = PythonPackageManagerUI(packageManager, sink)
  }
}