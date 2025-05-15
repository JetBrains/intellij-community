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
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Version
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil
import com.jetbrains.python.inspections.quickfix.InstallPackageQuickFix
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.toInstallRequest
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import com.jetbrains.python.packaging.ui.PyChooseRequirementsDialog
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JLabel
import javax.swing.UIManager

/**
 * PyCharm doesn't provide any API for package management for external plugins.
 * The closest thing is [PythonPackageManager], although it is also subject to change
 */
@ApiStatus.Internal
object PyPackageInstallUtils {
  fun offeredPackageForNotFoundModule(project: Project, sdk: Sdk, moduleName: String): String? {
    val shouldToInstall = checkShouldToInstall(project, sdk, moduleName)
    if (!shouldToInstall)
      return null
    return PyPsiPackageUtil.moduleToPackageName(moduleName)
  }

  fun checkShouldToInstall(project: Project, sdk: Sdk, moduleName: String): Boolean {
    val packageName = PyPsiPackageUtil.moduleToPackageName(moduleName)
    return !checkIsInstalled(project, sdk, packageName) && checkExistsInRepository(packageName)
  }

  fun checkIsInstalled(project: Project, sdk: Sdk, packageName: String): Boolean {
    val isStdLib = (PyStdlibUtil.getPackages() as Set<*>).contains(packageName)
    if (isStdLib) {
      return true
    }
    val packageManager = getPackageManagerOrNull(project, sdk) ?: return false
    return packageManager.installedPackages.any { normalizePackageName(it.name) == packageName }
  }

  private fun checkExistsInRepository(packageName: String): Boolean {
    val normalizedName = normalizePackageName(packageName)
    return PyPIPackageUtil.INSTANCE.isInPyPI(normalizedName)
  }


  suspend fun confirmAndInstall(project: Project, sdk: Sdk, packageName: String, versionSpec: PyRequirementVersionSpec? = null) {
    val isConfirmed = withContext(Dispatchers.EDT) {
      confirmInstall(project, packageName)
    }
    if (!isConfirmed)
      return
    val result = installPackage(project, sdk, packageName, true, versionSpec = versionSpec)
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

    return pythonPackageManager.updatePackages(packageSpecification)
  }

  suspend fun installPackage(
    project: Project,
    sdk: Sdk,
    packageName: String,
    withBackgroundProgress: Boolean,
    versionSpec: PyRequirementVersionSpec? = null,
    options: List<String> = emptyList(),
  ): Result<List<PythonPackage>> {
    return try {
      val pythonPackageManager = runCatching {
        PythonPackageManager.forSdk(project, sdk)
      }.getOrElse {
        return Result.failure(it)
      }

      val spec = withContext(Dispatchers.IO) {
        pythonPackageManager.createPackageSpecificationWithSpec(packageName, versionSpec)
      } ?: return Result.failure(Exception("Package $packageName not found in any repository"))

      return pythonPackageManager.installPackage(spec.toInstallRequest(), options, withBackgroundProgress)
    }
    catch (t: Throwable) {
      Result.failure(t)
    }
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


  fun invokeInstallPackage(project: Project, pythonSdk: Sdk, packageName: String, point: RelativePoint, versionSpec: PyRequirementVersionSpec? = null) {
    PyPackageCoroutine.launch(project) {
      runPackagingOperationOrShowErrorDialog(pythonSdk, PyBundle.message("python.new.project.install.failed.title", packageName),
                                             packageName) {
        val loadBalloon = showBalloon(point, PyBundle.message("python.packaging.installing.package", packageName), BalloonStyle.INFO)
        try {
          confirmAndInstall(project, pythonSdk, packageName, versionSpec = versionSpec)
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
  }
  catch (_: Throwable) {
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

