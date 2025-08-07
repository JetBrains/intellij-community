// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree

import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.isReadOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

private interface PackageHandler {
  suspend fun handle(treeTable: PyPackagesTreeTable, pkg: DisplayablePackage, event: MouseEvent)

  companion object {
    fun forPackage(pkg: DisplayablePackage): PackageHandler? = when (pkg) {
      is InstallablePackage -> InstallablePackageHandler()
      is InstalledPackage -> InstalledPackageHandler()
      else -> null
    }
  }
}

private class InstallablePackageHandler : PackageHandler {
  override suspend fun handle(treeTable: PyPackagesTreeTable, pkg: DisplayablePackage, event: MouseEvent) {
    val project: Project = treeTable.project
    val packagingService = project.service<PyPackagingToolWindowService>()
    if (packagingService.currentSdk?.isReadOnly != false)
      return
    val details = packagingService.detailsForPackage(pkg) ?: return

    withContext(Dispatchers.Main) {
      PyPackagesUiComponents.createAvailableVersionsPopup(pkg, details, project)
        .show(RelativePoint(event))
      treeTable.tree.requestFocus()
    }
  }
}

private class InstalledPackageHandler : PackageHandler {
  override suspend fun handle( treeTable: PyPackagesTreeTable, pkg: DisplayablePackage, event: MouseEvent) {
    val project: Project = treeTable.project
    val packagingService = project.service<PyPackagingToolWindowService>()
    if (packagingService.currentSdk?.isReadOnly != false)
      return
    if (pkg !is InstalledPackage || !pkg.canBeUpdated) return

    pkg.nextVersion?.let { version ->
      val pkgToUpdate = PythonPackage(pkg.name, version.presentableText, false)
      packagingService.installPackage(pkgToUpdate)
    }
  }
}

internal class PyPackageTableMouseAdapter(private val treeTable: PyPackagesTreeTable) : MouseAdapter() {

  override fun mouseClicked(e: MouseEvent) {
    if (!isValidLeftClick(e)) return

    val selectedPackage = findTargetPackageAtPoint(e) ?: return

    PyPackageCoroutine.launch(treeTable.project, Dispatchers.IO) {
      PackageHandler.forPackage (selectedPackage)?.handle(treeTable, selectedPackage, e)
    }
  }

  private fun isValidLeftClick(e: MouseEvent): Boolean =
    e.mouseButton == MouseButton.Left && e.clickCount == SINGLE_CLICK

  private fun findTargetPackageAtPoint(e: MouseEvent): DisplayablePackage? {
    if (e.source != treeTable.table) return null

    val row = treeTable.table.rowAtPoint(e.point)
    return if (row >= 0) treeTable.items.getOrNull(row) else null
  }

  companion object {
    private const val SINGLE_CLICK = 1
  }
}