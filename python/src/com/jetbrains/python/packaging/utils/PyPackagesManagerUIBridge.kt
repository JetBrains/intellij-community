// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.utils

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManagerUI
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPyRequirementsBackground
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal object PyPackagesManagerUIBridge {
  @JvmStatic
  fun runInstallInBackground(project: Project, sdk: Sdk, requirements: List<PyRequirement>?, extraArgs: List<String>, listener: PyPackageManagerUI.Listener?) {
    PyPackageCoroutine.launch(project) {
      val interpreter = sdk.pythonInterpreterResultAsync().getOr { return@launch listener.reportBrokenSdk(it.error) }
      val manager = PythonPackageManagerUI.forPythonInterpreter(project, interpreter)
      listener?.started()
      manager.installPyRequirementsBackground(requirements?.toList() ?: emptyList(), extraArgs)
      listener?.finished(emptyList())
    }
  }

  @JvmStatic
  fun runUninstallInBackground(project: Project, sdk: Sdk, packages: List<PyPackage>, listener: PyPackageManagerUI.Listener?) {
    PyPackageCoroutine.getScope(project).launch {
      val interpreter = sdk.pythonInterpreterResultAsync().getOr { return@launch listener.reportBrokenSdk(it.error) }
      val manager = PythonPackageManagerUI.forPythonInterpreter(project, interpreter)
      listener?.started()
      manager.uninstallPackagesBackground(packages.map { it.name })
      listener?.finished(emptyList())
    }
  }
}

/** Tells the listener that the work did not start because the SDK names no interpreter. */
private fun PyPackageManagerUI.Listener?.reportBrokenSdk(error: PyError) {
  this ?: return
  started()
  finished(listOf(ExecutionException(error.message)))
}
