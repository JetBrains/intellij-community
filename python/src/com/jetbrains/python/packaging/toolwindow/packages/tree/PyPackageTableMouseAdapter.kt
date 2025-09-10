// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree

import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.*
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.isReadOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

internal class PyPackageTableMouseAdapter(private val treeTable: PyPackagesTreeTable) : MouseAdapter() {
  private val project: Project = treeTable.project
  private val service = project.service<PyPackagingToolWindowService>()

  override fun mouseClicked(e: MouseEvent) {
    if (!isValidLeftClick(e)) return

    if (service.currentSdk?.isReadOnly != false) return

    val selectedPackage = findTargetPackageAtPoint(e) ?: return

    PyPackageCoroutine.launch(treeTable.project, Dispatchers.IO) {
      when (selectedPackage) {
        is InstallablePackage -> installablePackageMouseAdapter(treeTable, selectedPackage, e)
        is InstalledPackage -> installedPackageMouseAdapter(selectedPackage)
        is ErrorNode -> errorNodeMouseAdapter(selectedPackage)
        is RequirementPackage, is ExpandResultNode -> null
      }
    }
  }

  private fun isValidLeftClick(e: MouseEvent): Boolean =
    e.mouseButton == MouseButton.Left && e.clickCount == SINGLE_CLICK

  private fun findTargetPackageAtPoint(e: MouseEvent): DisplayablePackage? {
    if (e.source != treeTable.table) return null

    val row = treeTable.table.rowAtPoint(e.point)
    return if (row >= 0) treeTable.items.getOrNull(row) else null
  }

  private suspend fun installablePackageMouseAdapter(treeTable: PyPackagesTreeTable, pkg: InstallablePackage, event: MouseEvent) {
    val details = service.detailsForPackage(pkg) ?: return

    withContext(Dispatchers.Main) {
      PyPackagesUiComponents.createAvailableVersionsPopup(pkg, details, project)
        .show(RelativePoint(event))
      treeTable.tree.requestFocus()
    }
  }

  private suspend fun installedPackageMouseAdapter(pkg: InstalledPackage) {
    if (!pkg.canBeUpdated) return

    pkg.nextVersion?.let { version ->
      val pkgToUpdate = PythonPackage(pkg.name, version.presentableText, false)
      service.installPackage(pkgToUpdate)
    }
  }

  private suspend fun errorNodeMouseAdapter(pkg: ErrorNode) {
    pkg.quickFix.action.invoke()
    service.refreshInstalledPackages()
  }

  companion object {
    private const val SINGLE_CLICK = 1
  }
}