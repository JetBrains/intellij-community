// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.ui.PyInstallPackageDialog
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackages
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers

internal class InstallPackageAction : ModifyPackagesActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedPackages = e.selectedPackages.filterIsInstance<InstallablePackage>()
    if (selectedPackages.size > 1) {
      PyPackageCoroutine.launch(project, Dispatchers.Default) {
        val pyPackages = selectedPackages.mapNotNull { pkg ->
          pkg.repository.findPackageSpecification(pyRequirement(pkg.name))
        }
        val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(pyPackages)
        PyPackagingToolWindowService.getInstance(project).installPackage(installRequest)
      }
      return
    }
    val pkg = e.selectedPackage as? InstallablePackage ?: return
    PyInstallPackageDialog(project).show(pkg.name)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && e.selectedPackages.all { it is InstallablePackage }
  }
}