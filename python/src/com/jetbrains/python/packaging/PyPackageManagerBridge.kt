// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.findPythonInterpreter
import com.intellij.python.sdk.backend.findPythonInterpreterIfReady
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPyRequirementsBackground
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal open class PyPackageManagerBridge(private val sdk: Sdk) : PyPackageManager(sdk) {
  /**
   * The manager of [sdk], from the first open project that can use it. The old API is sync, but its blocking methods
   * run off the EDT, so they wait for the registry of each project. This bridge does not know which project the SDK
   * belongs to.
   */
  private suspend fun packageManager(): PythonPackageManager? = getOpenedProjects().firstNotNullOfOrNull { project ->
    project.findPythonInterpreter(sdk)?.let { PythonPackageManager.forPythonInterpreter(project, it) }
  }

  @Throws(ExecutionException::class)
  override fun install(requirements: MutableList<PyRequirement>?, extraArgs: MutableList<String>) {
    runBlockingMaybeCancellable {
      val manager = packageManager() ?: return@runBlockingMaybeCancellable
      PythonPackageManagerUI.forPackageManager(manager).installPyRequirementsBackground(requirements ?: emptyList(), extraArgs)
    }
  }

  override fun refreshAndGetPackages(alwaysRefresh: Boolean): List<PyPackage?> {
    val pythonPackages = if (alwaysRefresh) {
      runBlockingMaybeCancellable {
        val manager = packageManager() ?: return@runBlockingMaybeCancellable emptyList()
        manager.reloadPackages().onFailure {
          @Suppress("UsagesOfObsoleteApi")
          thisLogger().warn("Failed to reload packages", PyExecutionException(it))
        }.getOrNull() ?: emptyList()
      }
    }
    else {
      runBlockingMaybeCancellable {
        packageManager()?.listInstalledPackages() ?: emptyList()
      }
    }

    return pythonPackages.map { PyPackage(it.name, it.version) }
  }


  override fun getPackages(): List<PyPackage> {
    // A snapshot read, which can run on the EDT, so it cannot detect the interpreter. It asks the registry of each open
    // project, because this bridge does not know which project the SDK belongs to.
    val (project, interpreter) = getOpenedProjects().firstNotNullOfOrNull { project ->
      project.findPythonInterpreterIfReady(sdk)?.let { project to it }
    } ?: return emptyList()
    return PythonPackageManager.forPythonInterpreter(project, interpreter).listInstalledPackagesSnapshot()
      .map { PyPackage(it.name, it.version) }
  }
}
