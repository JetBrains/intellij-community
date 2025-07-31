// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.ui.PyChooseRequirementsDialog
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


internal object PyPackageManagerUiConfirmationHelpers {
  private const val CONFIRM_PACKAGE_INSTALLATION_PROPERTY: String = "python.confirm.package.installation"

  internal suspend fun getConfirmedPackages(pyRequirements: List<PyRequirement>, project: Project): List<PyRequirement> {
    if (pyRequirements.isEmpty())
      return emptyList()
    val confirmationEnabled = PropertiesComponent.getInstance()
      .getBoolean(CONFIRM_PACKAGE_INSTALLATION_PROPERTY, true)

    if (!confirmationEnabled)
      return pyRequirements

    if (pyRequirements.size == 1) {
      val pyPackage = pyRequirements.first()
      if (askSingleFileConfirmation(pyPackage, project)) {
        return listOf(pyPackage)
      }
      else {
        PyPackagesUsageCollector.installAllCanceledEvent.log()
        return listOf()
      }
    }

    return withContext(Dispatchers.EDT) {
      val dialog = PyChooseRequirementsDialog(project, pyRequirements) { it.presentableText }
      val result = dialog.showAndGet()
      if (!result) {
        PyPackagesUsageCollector.installAllCanceledEvent.log()
        listOf()
      }
      else {
        dialog.markedElements
      }
    }
  }

  private suspend fun askSingleFileConfirmation(pyRequirement: PyRequirement, project: Project): Boolean = withContext(Dispatchers.EDT) {
    MessageDialogBuilder.yesNo(
      PyBundle.message("python.packaging.dialog.title.install.package.confirmation"),
      PyBundle.message("python.packaging.dialog.message.install.package.confirmation", pyRequirement.presentableText))
      .icon(AllIcons.General.WarningDialog)
      .doNotAsk(ConfirmPackageInstallationDoNotAskOption())
      .ask(project)
  }


  private class ConfirmPackageInstallationDoNotAskOption : DoNotAskOption.Adapter() {
    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
      if (isSelected && exitCode == Messages.OK) {
        PropertiesComponent.getInstance().setValue(CONFIRM_PACKAGE_INSTALLATION_PROPERTY, false, true)
      }
    }
  }
}