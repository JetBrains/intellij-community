// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.table

import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.hover.TableHoverListener
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

internal class PyPackageTableMouseAdapter(private val table: PyPackagesTable) : MouseAdapter() {
  val project: Project = table.project

  val service
    get() = project.service<PyPackagingToolWindowService>()

  override fun mouseClicked(e: MouseEvent) {
    // double click or click on package name column, nothing to be done
    if (e.mouseButton != MouseButton.Left ||
        e.clickCount != 1 ||
        table.columnAtPoint(e.point) != 1) {
      return
    }

    val hoveredRow = TableHoverListener.getHoveredRow(table)
    val selectedPackage = table.items.getOrNull(hoveredRow) ?: table.selectedItem() ?: return

    if (selectedPackage is InstallablePackage) {
      PyPackageCoroutine.launch(project) {
        val details = service.detailsForPackage(selectedPackage) ?: return@launch
        withContext(Dispatchers.EDT) {
          PyPackagesUiComponents.createAvailableVersionsPopup(selectedPackage, details, project).show(RelativePoint(e))
        }
      }
      return
    }

    if (selectedPackage is InstalledPackage &&
        selectedPackage.canBeUpdated &&
        selectedPackage.repository != null &&
        selectedPackage.nextVersion != null) {
      val specification = selectedPackage.repository.findPackageSpecification(selectedPackage.name) ?: return

      PyPackageCoroutine.launch(project) {
        project.service<PyPackagingToolWindowService>().updatePackage(specification)
      }
    }
  }
}