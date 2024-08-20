// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackage
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.launch

internal class UpdatePackageToLatestAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val pkg = e.selectedPackage as? InstalledPackage ?: return

    val service = PyPackagingToolWindowService.getInstance(project)

    PyPackageCoroutine.getIoScope(project).launch {
      val specification = pkg.repository.createPackageSpecification(pkg.name, pkg.nextVersion!!.presentableText)
      service.updatePackage(specification)
    }
  }

  override fun update(e: AnActionEvent) {
    val pkg = e.selectedPackage as? InstalledPackage

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

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}