// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Version
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.installer.*
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object PySdkToInstallManager {
  /**
   * Software Release Installer for Apple Software Package (pkg) files
   */
  class PkgBinaryInstaller : ResourceTypeBinaryInstaller(ResourceType.APPLE_SOFTWARE_PACKAGE) {
    override fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine {
      return ExecUtil.sudoCommand(
        GeneralCommandLine("installer", "-pkg", path.absolutePathString(), "-target", "/"),
        PySdkBundle.message("python.sdk.running.sudo.prompt", resource.fileName)
      )
    }
  }

  /**
   * Software Release Installer for Microsoft Window Executable (exe) files
   */
  class ExeBinaryInstaller : ResourceTypeBinaryInstaller(ResourceType.MICROSOFT_WINDOWS_EXECUTABLE) {
    override fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine {
      return GeneralCommandLine(path.absolutePathString(), "/repair", "/quiet", "InstallAllUsers=0")
    }
  }

  private val cpythonInstallers = listOf(ExeBinaryInstaller(), PkgBinaryInstaller())

  fun getAvailableVersionsToInstall(): Map<LanguageLevel, BinaryInstallation> {
    return getLanguageLevelInstallations().mapNotNull { (k, v) ->
      v.firstOrNull()?.let { k to it }
    }.toMap()
  }

  fun install(binaryInstallation: BinaryInstallation, project: Project?, indicator: ProgressIndicator) {
    val (release, binary, installer) = binaryInstallation
    installer.install(binary, indicator) {
      PySdkToInstallCollector.logSdkDownload(
        project, release.version, PySdkToInstallCollector.DownloadResult.OK
      )
    }
    PySdkToInstallCollector.logSdkInstall(
      project, release.version, PySdkToInstallCollector.InstallationResult.OK
    )
  }

  fun install(sdk: PySdkToInstall, project: Project?, indicator: ProgressIndicator) {
    install(sdk.installation, project, indicator)
  }

  @RequiresEdt
  fun install(sdk: PySdkToInstall, module: Module?, systemWideSdksDetector: () -> List<PyDetectedSdk>): PyDetectedSdk? {
    val project = module?.project
    try {
      return ProgressManager.getInstance().run(
        object : Task.WithResult<PyDetectedSdk?, Exception>(project, PyBundle.message("python.sdk.installing", sdk.name), true) {
          override fun compute(indicator: ProgressIndicator): PyDetectedSdk? {
            install(sdk, project, indicator)
            return findInstalledSdk(
              languageLevel = Version.parseVersion(sdk.installation.release.version).toLanguageLevel(),
              project = project,
              systemWideSdksDetector = systemWideSdksDetector
            )
          }
        }
      )
    }
    catch (ex: BinaryInstallerException) {
      LOGGER.info(ex)
      PySdkToInstallCollector.logInstallerException(project, sdk.installation.release, ex)
      showErrorNotification(project, sdk.installation.release, ex)
    }
    return null
  }

  private fun showErrorNotification(project: Project?, release: Release, ex: BinaryInstallerException) {
    val title = when (ex) {
      is PrepareException -> PyBundle.message("python.sdk.download.failed.title", release.title)
      else -> PyBundle.message("python.sdk.installation.failed.title", release.title)
    }

    val message = when (ex) {
      is CancelledProcessException, is CancelledPrepareException -> PyBundle.message("python.sdk.installation.cancelled.message")
      is PrepareException -> PyBundle.message("python.sdk.download.failed.message")
      else -> PyBundle.message("python.sdk.try.to.install.python.manually")
    }
    Messages.showErrorDialog(project, message, title)
  }


  private fun getLanguageLevelInstallations(product: Product = Product.CPython): Map<LanguageLevel, List<BinaryInstallation>> {
    return SdksKeeper.pythonReleasesByLanguageLevel().mapValues { (_, releases) ->
      val releaseBinaries = releases
        .filter { it.product == product }
        .flatMap { release ->
          release.binaries?.filter { it.isCompatible() }?.map { release to it } ?: emptyList()
        }

      releaseBinaries.flatMap { (release, binary) ->
        cpythonInstallers.filter { it.canInstall(binary) }.map { installer ->
          BinaryInstallation(release, binary, installer)
        }
      }
    }
  }


  private fun findInstalledSdk(languageLevel: LanguageLevel?,
                               project: Project?,
                               systemWideSdksDetector: () -> List<PyDetectedSdk>): PyDetectedSdk? {
    LOGGER.debug("Resetting system-wide sdks detectors")
    resetSystemWideSdksDetectors()

    return systemWideSdksDetector()
      .also { sdks ->
        LOGGER.debug { sdks.joinToString(prefix = "Detected system-wide sdks: ") { it.homePath ?: it.name } }
      }
      .filter {
        val detectedLevel = PythonSdkFlavor.getFlavor(it)?.let { flavor ->
          flavor.getLanguageLevelFromVersionString(flavor.getVersionString(it.homePath!!))
        }
        languageLevel?.equals(detectedLevel) ?: true
      }
      .also {
        PySdkToInstallCollector.logSdkLookup(
          project,
          languageLevel.toString(),
          when (it.isNotEmpty()) {
            true -> PySdkToInstallCollector.LookupResult.FOUND
            false -> PySdkToInstallCollector.LookupResult.NOT_FOUND
          }
        )
      }
      .firstOrNull()
  }

}