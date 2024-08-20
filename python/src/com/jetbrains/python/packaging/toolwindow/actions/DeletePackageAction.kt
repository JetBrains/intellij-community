// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesTable
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers

internal class DeletePackageAction(val table: PyPackagesTable<*>) : DumbAwareAction(PyBundle.message("python.toolwindow.packages.delete.package")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val pkg = table.selectedItem() as? InstalledPackage ?: return

    val service = project.service<PyPackagingToolWindowService>()

    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      service.deletePackage(pkg)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = table.selectedItem() is InstalledPackage
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}