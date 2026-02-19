// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.selectedPackages
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers

internal class UpdatePackageToLatestAction : ModifyPackagesActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val packages = getPackagesForUpdate(e)
    if (packages.isEmpty()) {
      return
    }

    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val pyPackages = packages.mapNotNull { pkg ->
        val versionString = pkg.nextVersion?.presentableText
        val requirement = pyRequirement(pkg.name, versionString?.let { pyRequirementVersionSpec(it) })
        pkg.repository?.findPackageSpecification(requirement)
      }
      val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(pyPackages)
      project.service<PyPackagingToolWindowService>().installPackage(installRequest)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (!e.presentation.isEnabledAndVisible) {
      return
    }
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


  private fun getPackagesForUpdate(e: AnActionEvent) =
    e.selectedPackages.filterIsInstance<InstalledPackage>().filter {
      it.canBeUpdated
    }
}