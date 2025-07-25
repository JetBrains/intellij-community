// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackages
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent

internal class InstallPackageAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedPackages = e.selectedPackages.filterIsInstance<InstallablePackage>()
    if (selectedPackages.size > 1) {
      PyPackageCoroutine.launch(project, Dispatchers.Default) {
        val pyPackages = selectedPackages.mapNotNull { pkg ->
          pkg.repository.findPackageSpecification(pyRequirement(pkg.name))
        }
        val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(pyPackages)
        project.service<PyPackagingToolWindowService>().installPackage(installRequest)
      }
      return
    }
    val pkg = e.selectedPackage as? InstallablePackage ?: return



    PyPackageCoroutine.launch(project, Dispatchers.Default) {
      val service = PyPackagingToolWindowService.getInstance(project)
      val details = service.detailsForPackage(pkg) ?: return@launch
      withContext(Dispatchers.Main) {
        val popup = PyPackagesUiComponents.createAvailableVersionsPopup(pkg, details, project)
        popup.show(RelativePoint(e.inputEvent as MouseEvent))
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.selectedPackages.all { it is InstallablePackage }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}