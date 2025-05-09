// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.packaging.management.toInstallRequest
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
      PyPackageCoroutine.launch(project, Dispatchers.IO) {
        selectedPackages.forEach { pkg ->
          val specification = pkg.repository.createPackageSpecification(pkg.name, null)
          project.service<PyPackagingToolWindowService>().installPackage(specification.toInstallRequest())
        }
      }
      return
    }
    val pkg = e.selectedPackage as? InstallablePackage ?: return



    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val service = PyPackagingToolWindowService.getInstance(project)
      val details = service.detailsForPackage(pkg)
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