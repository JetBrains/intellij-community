// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPyRequirementsBackground
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal open class PyPackageManagerBridge(sdk: Sdk) : PyTargetEnvironmentPackageManager(sdk) {
  protected val packageManager: PythonPackageManager = PythonPackageManager.forSdk(guessProject(), sdk = sdk)
  protected val packageManagerUI: PythonPackageManagerUI = PythonPackageManagerUI.forPackageManager(packageManager)

  override fun installManagement() {}
  override fun hasManagement(): Boolean = true

  @Throws(ExecutionException::class)
  override fun install(requirements: MutableList<PyRequirement>?, extraArgs: MutableList<String>) {
    runBlockingMaybeCancellable {
      packageManagerUI.installPyRequirementsBackground(requirements ?: emptyList(), extraArgs)
    }
  }

  override fun refreshAndGetPackages(alwaysRefresh: Boolean): List<PyPackage?> {
    val pythonPackages = if (alwaysRefresh) {
      runBlockingMaybeCancellable {
        packageManager.reloadPackages().onFailure {
          @Suppress("UsagesOfObsoleteApi")
          thisLogger().warn("Failed to reload packages", PyExecutionException(it))
        }.getOrNull() ?: emptyList()
      }
    }
    else {
      runBlockingMaybeCancellable {
        packageManager.listInstalledPackages()
      }
    }

    return pythonPackages.map { PyPackage(it.name, it.version) }
  }


  @Throws(ExecutionException::class)
  override fun collectPackages(): List<PyPackage> {
    return runBlockingMaybeCancellable {
      packageManagerUI.manager.listInstalledPackages()
    }.map { PyPackage(it.name, it.version) }
  }


  override fun getPackages(): List<PyPackage> {
    return packageManagerUI.manager.listInstalledPackagesSnapshot().map { PyPackage(it.name, it.version) }
  }

  private fun guessProject() = getOpenedProjects().firstOrNull() ?: ProjectManager.getInstance().defaultProject
}
