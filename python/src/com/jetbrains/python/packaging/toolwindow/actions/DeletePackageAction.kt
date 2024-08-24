// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackages
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers

internal class DeletePackageAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedPackages = e.selectedPackages.filterIsInstance<InstalledPackage>()
    if (selectedPackages.size > 1) {
      PyPackageCoroutine.launch(project, Dispatchers.IO) {
        selectedPackages.forEach { pkg ->
          project.service<PyPackagingToolWindowService>().deletePackage(pkg)
        }
      }
      return
    }

    val pkg = e.selectedPackage as? InstalledPackage ?: return

    val service = project.service<PyPackagingToolWindowService>()
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      service.deletePackage(pkg)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.selectedPackages.all { it is InstalledPackage }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}