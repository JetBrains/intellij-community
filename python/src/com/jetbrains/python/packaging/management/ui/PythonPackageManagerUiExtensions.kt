// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management.ui

import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import org.jetbrains.annotations.ApiStatus

/**
 * @return List of all installed packages or null if the operation was failed.
 */
@ApiStatus.Internal
suspend fun PythonPackageManagerUI.updatePackageBackground(
  pyPackage: String,
): List<PythonPackage>? =
  updatePackagesByNamesBackground(listOf(pyPackage))

/**
 * @return List of all installed packages or null if the operation was failed.
 */
@ApiStatus.Internal
suspend fun PythonPackageManagerUI.updatePackagesByNamesBackground(
  packages: List<String>,
): List<PythonPackage>? {
  val specifications = packages.mapNotNull {
    manager.findPackageSpecification(it)
  }
  return updatePackagesBackground(specifications)
}

@ApiStatus.Internal
fun PythonPackageManagerUI.launchInstallPackageWithBalloonBackground(packageName: String, point: RelativePoint, versionSpec: PyRequirementVersionSpec? = null) {
  PyPackageCoroutine.launch(project) {
    val loadBalloon = PythonPackageManagerUIHelpers.showBalloon(point, PyBundle.message("python.packaging.installing.package", packageName),
                                                                PythonPackageManagerUIHelpers.BalloonStyle.INFO)
    try {
      installPyRequirementsWithConfirmation(listOf(pyRequirement(packageName, versionSpec)))
      loadBalloon.hide()
      PyPackagesUsageCollector.installPackageFromConsole.log(project)
      PythonPackageManagerUIHelpers.showBalloon(point, PyBundle.message("python.packaging.notification.description.installed.packages", packageName), PythonPackageManagerUIHelpers.BalloonStyle.SUCCESS)
    }
    catch (t: Throwable) {
      loadBalloon.hide()
      PyPackagesUsageCollector.failInstallPackageFromConsole.log(project)
      PythonPackageManagerUIHelpers.showBalloon(point, PyBundle.message("python.new.project.install.failed.title", packageName), PythonPackageManagerUIHelpers.BalloonStyle.ERROR)
      throw t
    }
  }
}

/**
 * @return List of all installed packages or null if the operation was failed.
 */
@ApiStatus.Internal
suspend fun PythonPackageManagerUI.installPyRequirementsBackground(
  packages: List<PyRequirement>,
  options: List<String> = emptyList(),
): List<PythonPackage>? {
  //Wait here to load spec
  manager.waitForInit()
  val specifications = packages.mapNotNull {
    manager.repositoryManager.findPackageSpecification(it)
  }
  return installPackagesRequestBackground(PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specifications),
                                          options = options)
}

@ApiStatus.Internal
suspend fun PythonPackageManagerUI.installPyRequirementsDetachedBackground(
  packages: List<PyRequirement>,
  options: List<String> = emptyList(),
): List<PythonPackage>? {
  manager.waitForInit()
  val specifications = packages.mapNotNull {
    manager.repositoryManager.findPackageSpecification(it)
  }
  return installPackagesRequestDetachedBackground(PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specifications),
                                                  options = options)
}


@ApiStatus.Internal
suspend fun PythonPackageManagerUI.installPackagesBackground(
  packages: List<String>,
  options: List<String> = emptyList(),
): List<PythonPackage>? {
  //Wait here to load spec
  manager.waitForInit()
  val specifications = packages.mapNotNull {
    manager.findPackageSpecification(it)
  }
  return installPackagesRequestBackground(PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specifications),
                                          options = options)
}

/**
 * @return List of all installed packages or null if the operation was failed.
 */
@ApiStatus.Internal
suspend fun PythonPackageManagerUI.installPackageBackground(
  pyPackage: String,
  versionSpec: PyRequirementVersionSpec? = null,
  options: List<String> = emptyList(),
): List<PythonPackage>? = installPyRequirementsBackground(listOf(pyRequirement(pyPackage, versionSpec)),
                                                          options = options)

