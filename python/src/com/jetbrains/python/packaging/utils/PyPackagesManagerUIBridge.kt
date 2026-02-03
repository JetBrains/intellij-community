// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
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
      val manager = PythonPackageManagerUI.forSdk(project, sdk)
      listener?.started()
      manager.installPyRequirementsBackground(requirements?.toList() ?: emptyList(), extraArgs)
      listener?.finished(emptyList())
    }
  }

  @JvmStatic
  fun runUninstallInBackground(project: Project, sdk: Sdk, packages: List<PyPackage>, listener: PyPackageManagerUI.Listener?) {
    PyPackageCoroutine.getScope(project).launch {
      val manager = PythonPackageManagerUI.forSdk(project, sdk)
      listener?.started()
      manager.uninstallPackagesBackground(packages.map { it.name })
      listener?.finished(emptyList())
    }
  }
}