// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.progressBar
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackage
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent

internal class ChangeVersionPackageAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val pkg = e.selectedPackage as? InstalledPackage ?: return
    val service = PyPackagingToolWindowService.getInstance(project)
    val controller = service.toolWindowPanel ?: return
    val progressBar = e.progressBar
    PyPackageCoroutine.getIoScope(project).launch {
      progressBar?.isVisible = true
      try {
        val details = service.detailsForPackage(pkg)
        withContext(Dispatchers.EDT) {
          PyPackagesUiComponents.createAvailableVersionsPopup(pkg, details, project, controller).show(
            RelativePoint(e.inputEvent as MouseEvent))
        }
      }
      finally {
        progressBar?.isVisible = false
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val pkg = e.selectedPackage as? InstalledPackage
    e.presentation.isEnabledAndVisible = pkg != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

}