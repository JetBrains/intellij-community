// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.backend.observation.trackActivity
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.management.runPackagingTool
import com.jetbrains.python.sdk.headless.PythonActivityKey
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class PipPythonPackageManager(project: Project, sdk: Sdk) : PipBasedPackageManager(project, sdk) {
  @Volatile
  override var installedPackages: List<PythonPackage> = emptyList()
    set

  override val repositoryManager: PipRepositoryManager = PipRepositoryManager(project, sdk)

  override suspend fun reloadPackages(): Result<List<PythonPackage>> = project.trackActivity(PythonActivityKey) {
    val result = runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.packaging.operation.failed.title")) {
      val output = runPackagingTool("list", emptyList(), PyBundle.message("python.packaging.list.progress"))

      val packages = output.lineSequence()
        .filter { it.isNotBlank() }
        .map {
          val line = it.split("\t")
          PythonPackage(line[0], line[1])
        }
        .sortedWith(compareBy(PythonPackage::name))
        .toList()
      Result.success(packages)
    }

    if (result.isFailure) return@trackActivity result

    installedPackages = result.getOrThrow()

    ApplicationManager.getApplication().messageBus.apply {
      syncPublisher(PACKAGE_MANAGEMENT_TOPIC).packagesChanged(sdk)
      syncPublisher(PyPackageManager.PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)
    }

    return@trackActivity result
  }

}