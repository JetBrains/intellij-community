// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.management.toInstallRequest
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackages
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class InstallWithOptionsPackageAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val pkg = e.selectedPackage as? InstallablePackage ?: return


    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val service = PyPackagingToolWindowService.getInstance(project)
      val details = service.detailsForPackage(pkg)

      installWithOptions(project, details)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.selectedPackage as? InstallablePackage != null && e.selectedPackages.size == 1
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  companion object {
    internal suspend fun installWithOptions(project: Project, details: PythonPackageDetails, version: String? = null) {
      val optionsString = withContext(Dispatchers.EDT) {
        Messages.showInputDialog(project,
                                 message("package.install.with.options.dialog.message"),
                                 message("action.PyInstallWithOptionPackage.text"),
                                 Messages.getQuestionIcon()
        )
      } ?: return

      val options = optionsString.split(' ').map { it.trim() }.filter { it.isNotBlank() }

      val specification = details.toPackageSpecification(version ?: details.availableVersions.first())
      project.service<PyPackagingToolWindowService>().installPackage(specification.toInstallRequest(), options)
    }

  }
}