// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.Messages
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil
import com.jetbrains.python.inspections.quickfix.InstallPackageQuickFix
import com.jetbrains.python.packaging.PyPIPackageUtil.INSTANCE
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackageSnapshot
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPyRequirementsBackground
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import com.jetbrains.python.packaging.ui.PyChooseRequirementsDialog
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * PyCharm doesn't provide any API for package management for external plugins.
 * The closest thing is [PythonPackageManager], although it is also subject to change
 */
@ApiStatus.Internal
object PyPackageInstallUtils {
  internal fun getConfirmedPackages(packageNames: List<PyRequirement>, project: Project): Set<PyRequirement> {
    val confirmationEnabled = PropertiesComponent.getInstance()
      .getBoolean(InstallPackageQuickFix.CONFIRM_PACKAGE_INSTALLATION_PROPERTY, true)

    if (!confirmationEnabled || packageNames.isEmpty()) return packageNames.toSet()

    val dialog = PyChooseRequirementsDialog(project, packageNames) { it.presentableTextWithoutVersion }

    if (!dialog.showAndGet()) {
      PyPackagesUsageCollector.installAllCanceledEvent.log()
      return emptySet()
    }

    return dialog.markedElements.toSet()
  }


  fun offeredPackageForNotFoundModule(project: Project, sdk: Sdk, moduleName: String): String? {
    val shouldToInstall = checkShouldToInstallSnapshot(project, sdk, moduleName)
    if (!shouldToInstall)
      return null
    return PyPsiPackageUtil.moduleToPackageName(moduleName)
  }

  fun checkShouldToInstallSnapshot(project: Project, sdk: Sdk, moduleName: String): Boolean {
    val packageName = PyPsiPackageUtil.moduleToPackageName(moduleName)
    return !checkIsInstalledSnapshot(project, sdk, packageName) && INSTANCE.isInPyPI(packageName)
  }

  fun checkIsInstalledSnapshot(project: Project, sdk: Sdk, packageName: String): Boolean {
    val isStdLib = PyStdlibUtil.getPackages()?.contains(packageName) ?: false
    if (isStdLib) {
      return true
    }
    val packageManager = PythonPackageManager.forSdk(project, sdk)
    return packageManager.hasInstalledPackageSnapshot(packageName)
  }

  suspend fun confirmAndInstall(project: Project, sdk: Sdk, requirement: PyRequirement) {
    val isConfirmed = withContext(Dispatchers.EDT) {
      confirmInstall(project, requirement.name)
    }
    if (!isConfirmed)
      return
    PythonPackageManagerUI.forSdk(project, sdk).installPyRequirementsBackground(listOf(requirement))
  }


  suspend fun confirmAndInstall(project: Project, sdk: Sdk, packageName: String, versionSpec: PyRequirementVersionSpec? = null) {
    confirmAndInstall(project, sdk, pyRequirement(packageName, versionSpec))
  }

  fun confirmInstall(project: Project, packageName: String): Boolean {
    val isWellKnownPackage = ApplicationManager.getApplication()
      .getService(PyPIPackageRanking::class.java)
      .packageRank.containsKey(packageName)
    val confirmationEnabled = PropertiesComponent.getInstance().getBoolean(InstallPackageQuickFix.CONFIRM_PACKAGE_INSTALLATION_PROPERTY, true)
    if (!isWellKnownPackage && confirmationEnabled) {
      val confirmed: Boolean = yesNo(PyBundle.message("python.packaging.dialog.title.install.package.confirmation"),
                                     PyBundle.message("python.packaging.dialog.message.install.package.confirmation", packageName))
        .icon(AllIcons.General.WarningDialog)
        .doNotAsk(ConfirmPackageInstallationDoNotAskOption())
        .ask(project)
      if (!confirmed) {
        return false
      }
    }
    return true
  }


  private class ConfirmPackageInstallationDoNotAskOption : DoNotAskOption.Adapter() {
    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
      if (isSelected && exitCode == Messages.OK) {
        PropertiesComponent.getInstance().setValue(InstallPackageQuickFix.CONFIRM_PACKAGE_INSTALLATION_PROPERTY, false, true)
      }
    }
  }
}


