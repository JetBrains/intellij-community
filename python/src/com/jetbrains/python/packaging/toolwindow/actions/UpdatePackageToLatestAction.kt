// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesTable
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent

internal class UpdatePackageToLatestAction(val table: PyPackagesTable<*>) : DumbAwareAction(PyBundle.message("python.toolwindow.packages.update.package")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val pkg = table.selectedItem()

    val service = project.service<PyPackagingToolWindowService>()
    if (pkg is InstalledPackage && pkg.canBeUpdated) {
      PyPackageCoroutine.getIoScope(project).launch {
        val specification = pkg.repository.createPackageSpecification(pkg.name, pkg.nextVersion!!.presentableText)
        service.updatePackage(specification)
      }
    }
    else if (pkg is InstallablePackage) {
      PyPackageCoroutine.getIoScope(project).launch {
        val details = service.detailsForPackage(pkg)
        withContext(Dispatchers.EDT) {
          PyPackagesUiComponents.createAvailableVersionsPopup(pkg, details, project, table.controller).show(
            RelativePoint(e.inputEvent as MouseEvent))
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val pkg = table.selectedItem() as? InstalledPackage

    val currentVersion = pkg?.currentVersion?.presentableText
    val nextVersion = pkg?.nextVersion?.presentableText
    if (currentVersion != null && nextVersion != null) {
      e.presentation.isEnabledAndVisible = true
      e.presentation.text = PyBundle.message("python.toolwindow.packages.update.package.version", currentVersion, nextVersion)
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }

  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}