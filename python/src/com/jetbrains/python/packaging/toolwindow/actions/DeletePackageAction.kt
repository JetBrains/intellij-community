// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackages
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers

internal class DeletePackageAction : ModifyPackagesActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedPackages = e.selectedPackages.filterIsInstance<InstalledPackage>().toTypedArray()
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      project.service<PyPackagingToolWindowService>().deletePackage(*selectedPackages)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && e.selectedPackages.all { it is InstalledPackage }
  }

}