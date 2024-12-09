// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.Version
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.PyPackageRequirementsInspection.InstallPackageQuickFix
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PyPackageInstallUtils {
  suspend fun confirmAndInstall(project: Project, sdk: Sdk, packageName: String) {
    val isConfirmed = withContext(Dispatchers.EDT) {
      confirmInstall(project, packageName)
    }
    if (!isConfirmed)
      return
    installPackage(project, sdk, packageName)
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

  suspend fun upgradePackage(project: Project, sdk: Sdk, packageName: String, version: String? = null): Result<List<PythonPackage>> {
    val pythonPackageManager = PythonPackageManager.forSdk(project, sdk)
    val packageSpecification = pythonPackageManager.repositoryManager.repositories.firstOrNull()?.createPackageSpecification(packageName, version)
                               ?: return Result.failure(Exception("Could not find any repositories"))

    return pythonPackageManager.updatePackage(packageSpecification)
  }


  suspend fun installPackage(project: Project, sdk: Sdk, packageName: String, version: String? = null): Result<List<PythonPackage>> {
    val pythonPackageManager = PythonPackageManager.forSdk(project, sdk)
    val packageSpecification = pythonPackageManager.repositoryManager.repositories.firstOrNull()?.createPackageSpecification(packageName, version)
                               ?: return Result.failure(Exception("Could not find any repositories"))

    return pythonPackageManager.installPackage(packageSpecification, emptyList<String>())
  }

  fun getPackageVersion(project: Project, sdk: Sdk, packageName: String): Version? {
    val pythonPackage = getPackage(project, sdk, packageName)
    val version = pythonPackage?.version ?: return null
    return Version.parseVersion(version)
  }

  private fun getPackage(
    project: Project,
    sdk: Sdk,
    packageName: String,
  ): PythonPackage? {
    val pythonPackageManager = PythonPackageManager.forSdk(project, sdk)
    val pythonPackage = pythonPackageManager.installedPackages.firstOrNull { it.name == packageName }
    return pythonPackage
  }

  suspend fun uninstall(project: Project, sdk: Sdk, libName: String) {
    val pythonPackageManager = PythonPackageManager.forSdk(project, sdk)
    val pythonPackage = getPackage(project, sdk, libName) ?: return
    pythonPackageManager.uninstallPackage(pythonPackage)
  }

  private class ConfirmPackageInstallationDoNotAskOption : DoNotAskOption.Adapter() {
    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
      if (isSelected && exitCode == Messages.OK) {
        PropertiesComponent.getInstance().setValue(InstallPackageQuickFix.CONFIRM_PACKAGE_INSTALLATION_PROPERTY, false, true)
      }
    }
  }
}

@Suppress("HardCodedStringLiteral")
fun getConfirmedPackages(packageNames: List<String>): List<String> {
  val confirmationEnabled = PropertiesComponent.getInstance().getBoolean(InstallPackageQuickFix.CONFIRM_PACKAGE_INSTALLATION_PROPERTY, true)
  if (!confirmationEnabled) {
    return packageNames
  }

  val packageRank = ApplicationManager.getApplication()
    .getService(PyPIPackageRanking::class.java)
    .packageRank

  val (knownPackages, nonWellKnownPackages) = packageNames.partition {
    packageRank.containsKey(it)
  }

  if (nonWellKnownPackages.isEmpty()) {
    return packageNames
  }

  val packagesToInstall = ArrayList(packageNames)
  val panel = panel {
    packageNames.forEach {
      row {
        checkBox(it).bindSelected({ true }, { isSelected ->
          if (isSelected)
            packagesToInstall.add(it)
          else
            packagesToInstall.remove(it)
        })
      }
    }
  }

  val dialog = dialog(PyBundle.message("python.packaging.dialog.title.install.package.confirmation"), panel, resizable = true)
  dialog.contentPanel.preferredSize = JBUI.size(maxOf(dialog.contentPanel.preferredSize.width, 600), dialog.preferredSize.height)

  val isOk = dialog.showAndGet()
  if (!isOk) {
    return emptyList()
  }
  return knownPackages + packagesToInstall
}