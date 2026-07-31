// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.packaging.management.PyPackageScope
import com.jetbrains.python.packaging.toolwindow.PyPackageIcons
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.showChangeVersionPopup
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackages
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Point

internal class ChangeVersionPackageAction : ModifyPackagesActionBase() {
  init {
    templatePresentation.icon = PyPackageIcons.ChangeVersion
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val pkg = e.selectedPackage as? InstalledPackage ?: return
    val service = PyPackagingToolWindowService.getInstance(project)
    val component = e.inputEvent?.component
    val anchor = component?.let { RelativePoint(it, Point(0, it.height)) }
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val details = service.detailsForPackage(pkg) ?: return@launch
      withContext(Dispatchers.EDT) {
        showChangeVersionPopup(
          project, details, PyPackageScope(pkg.workspaceMember, pkg.dependencyGroup), anchor,
          highlightVersion = pkg.nextVersion?.presentableText,
          currentVersion = pkg.instance.version,
        )
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && e.selectedPackage as? InstalledPackage != null && e.selectedPackages.size == 1
  }
}