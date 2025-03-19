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
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Version
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil
import com.jetbrains.python.inspections.quickfix.InstallPackageQuickFix
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.ui.PyChooseRequirementsDialog
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JLabel
import javax.swing.UIManager

object PyPackageInstallUtils {
  fun offeredPackageForNotFoundModule(project: Project, sdk: Sdk, moduleName: String): String? {
    val pipPackageName = PyPsiPackageUtil.moduleToPackageName(moduleName)

    val shouldToInstall = checkShouldToInstall(project, sdk, pipPackageName)
    if (!shouldToInstall)
      return null
    return pipPackageName
  }

  fun checkShouldToInstall(project: Project, sdk: Sdk, packageName: String): Boolean {
    return !checkIsInstalled(project, sdk, packageName) && checkExistsInRepository(project, sdk, packageName)
  }

  fun checkIsInstalled(project: Project, sdk: Sdk, packageName: String): Boolean {
    val packageManager = getPackageManagerOrNull(project, sdk) ?: return false
    val normalizedName = normalizePackageName(packageName)
    val isStdLib = PyStdlibUtil.getPackages()?.any { normalizePackageName(it) == normalizedName } == true
    if (isStdLib) {
      return true
    }
    return packageManager.installedPackages.any { normalizePackageName(it.name) == normalizedName }
  }

  fun checkExistsInRepository(project: Project, sdk: Sdk, packageName: String): Boolean {
    if (!PyPackageUtil.packageManagementEnabled(sdk, false, true)) {
      return false
    }

    if (ApplicationManager.getApplication().isUnitTestMode )
      return PyPIPackageUtil.INSTANCE.isInPyPI(packageName)

    val packageManager = getPackageManagerOrNull(project, sdk) ?: return false
    val repositoryManager = packageManager.repositoryManager
    val normalizedName = normalizePackageName(packageName)
    return repositoryManager.allPackages().any { normalizePackageName(it) == normalizedName }
  }


  suspend fun confirmAndInstall(project: Project, sdk: Sdk, packageName: String) {
    val isConfirmed = withContext(Dispatchers.EDT) {
      confirmInstall(project, packageName)
    }
    if (!isConfirmed)
      return
    val result = withBackgroundProgress(project = project, PyBundle.message("python.packaging.installing.package", packageName),
                                        cancellable = true) {
      installPackage(project, sdk, packageName)
    }
    result.getOrThrow()
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
    val pythonPackageManager = getPackageManagerOrNull(project, sdk)
    val packageSpecification = pythonPackageManager?.repositoryManager?.repositories?.firstOrNull()?.createPackageSpecification(packageName, version)
                               ?: return Result.failure(Exception("Could not find any repositories"))

    return pythonPackageManager.updatePackage(packageSpecification)
  }

  suspend fun initPackages(project: Project, sdk: Sdk) {
    val pythonPackageManager = getPackageManagerOrNull(project, sdk)
    if (pythonPackageManager?.installedPackages.isNullOrEmpty()) {
      withContext(Dispatchers.IO) {
        pythonPackageManager?.reloadPackages()
      }
    }
  }

  suspend fun installPackage(project: Project, sdk: Sdk, packageName: String, version: String? = null): Result<List<PythonPackage>> {
    val pythonPackageManager = getPackageManagerOrNull(project, sdk)
    val packageSpecification = pythonPackageManager?.repositoryManager?.repositories?.firstOrNull()?.createPackageSpecification(packageName, version)
                               ?: return Result.failure(Exception("Could not find any repositories"))

    return pythonPackageManager.installPackage(packageSpecification, emptyList())
  }

  /**
   * NOTE calling this functions REQUIRED init package list before the calling!
   */
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
    val pythonPackageManager = getPackageManagerOrNull(project, sdk)
    val installedPackages = pythonPackageManager?.installedPackages ?: return null

    val pythonPackage = installedPackages.firstOrNull { it.name == packageName }
    return pythonPackage
  }

  suspend fun uninstall(project: Project, sdk: Sdk, libName: String) {
    val pythonPackageManager = getPackageManagerOrNull(project, sdk) ?: return
    val pythonPackage = getPackage(project, sdk, libName) ?: return
    pythonPackageManager.uninstallPackage(pythonPackage)
  }



  fun invokeInstallPackage(project: Project, pythonSdk: Sdk, packageName: String, point: RelativePoint) {
    PyPackageCoroutine.launch(project) {
      runPackagingOperationOrShowErrorDialog(pythonSdk, PyBundle.message("python.new.project.install.failed.title", packageName),
                                             packageName) {
        val loadBalloon = showBalloon(point, PyBundle.message("python.packaging.installing.package", packageName), BalloonStyle.INFO)
        try {
          confirmAndInstall(project, pythonSdk, packageName)
          loadBalloon.hide()
          PyPackagesUsageCollector.installPackageFromConsole.log(project)
          showBalloon(point, PyBundle.message("python.packaging.notification.description.installed.packages", packageName), BalloonStyle.SUCCESS)
        }
        catch (t: Throwable) {
          loadBalloon.hide()
          PyPackagesUsageCollector.failInstallPackageFromConsole.log(project)
          showBalloon(point, PyBundle.message("python.new.project.install.failed.title", packageName), BalloonStyle.ERROR)
          throw t
        }
        Result.success(Unit)
      }
    }
  }

  private fun getPackageManagerOrNull(
    project: Project,
    sdk: Sdk,
  ): PythonPackageManager? = try {
    PythonPackageManager.forSdk(project, sdk)
  } catch (_: Throwable) {
    null
  }

  private class ConfirmPackageInstallationDoNotAskOption : DoNotAskOption.Adapter() {
    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
      if (isSelected && exitCode == Messages.OK) {
        PropertiesComponent.getInstance().setValue(InstallPackageQuickFix.CONFIRM_PACKAGE_INSTALLATION_PROPERTY, false, true)
      }
    }
  }

  private suspend fun showBalloon(point: RelativePoint, @NlsContexts.DialogMessage text: String, style: BalloonStyle): Balloon =
    withContext(Dispatchers.EDT) {
      val content = JLabel()
      val (borderColor, fillColor) = when (style) {
        BalloonStyle.SUCCESS -> JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR to JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND
        BalloonStyle.INFO -> JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR to JBUI.CurrentTheme.Banner.INFO_BACKGROUND
        BalloonStyle.ERROR -> JBUI.CurrentTheme.Validator.errorBorderColor() to JBUI.CurrentTheme.Validator.errorBackgroundColor()
      }
      val balloonBuilder = JBPopupFactory.getInstance()
        .createBalloonBuilder(content)
        .setBorderInsets(UIManager.getInsets("Balloon.error.textInsets"))
        .setBorderColor(borderColor)
        .setFillColor(fillColor)
        .setHideOnClickOutside(true)
        .setHideOnFrameResize(false)
      content.text = text
      val balloon = balloonBuilder.createBalloon()
      balloon.show(point, Balloon.Position.below)
      balloon
    }

  enum class BalloonStyle { ERROR, INFO, SUCCESS }
}

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

