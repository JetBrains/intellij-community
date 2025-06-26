// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackages
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.launch

internal class UpdatePackageToLatestAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val packages = getPackagesForUpdate(e)
    if (packages.isEmpty()) {
      return
    }

    PyPackageCoroutine.getIoScope(project).launch {
      val pyPackages = packages.mapNotNull { pkg ->
        pkg.repository?.findPackageSpecification(pkg.name, pkg.nextVersion?.presentableText)
      }
      val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(pyPackages)
      project.service<PyPackagingToolWindowService>().installPackage(installRequest)
    }
  }

  override fun update(e: AnActionEvent) {
    val packages = getPackagesForUpdate(e)

    e.presentation.apply {
      isEnabledAndVisible = !packages.isEmpty()
      text = packages.singleOrNull()?.let {
        val currentVersion = it.currentVersion?.presentableText
        val nextVersion = it.nextVersion?.presentableText
        PyBundle.message("python.toolwindow.packages.update.package.version", currentVersion, nextVersion)
      } ?: PyBundle.message("python.toolwindow.packages.update.packages")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private fun getPackagesForUpdate(e: AnActionEvent) =
    e.selectedPackages.filterIsInstance<InstalledPackage>().filter {
      it.canBeUpdated
    }
}