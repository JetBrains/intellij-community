// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesTable
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent

internal class InstallPackageAction(val table: PyPackagesTable<*>) : DumbAwareAction(PyBundle.message("python.toolwindow.packages.install.link")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val service = project.service<PyPackagingToolWindowService>()
    val pkg = table.selectedItem() as? InstallablePackage ?: return

    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val details = service.detailsForPackage(pkg)
      withContext(Dispatchers.Main) {
        val popup = PyPackagesUiComponents.createAvailableVersionsPopup(pkg, details, project, table.controller)
        popup.show(RelativePoint(e.inputEvent as MouseEvent))
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = table.selectedItem() is InstallablePackage
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}