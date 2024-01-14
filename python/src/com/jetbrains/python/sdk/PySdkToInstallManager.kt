// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.installer.*
import java.util.*

object PySdkToInstallManager {

  private val installers = listOf(ExeReleaseInstaller(), PkgReleaseInstaller())

  fun getAvailableVersionsToInstall(): Map<LanguageLevel, Pair<ReleaseInstaller, Release>> {
    return installers.fold(EnumMap(LanguageLevel::class.java)) { union, installer ->
      union.apply {
        getLatestReleases(installer).forEach { (languageLevel, release) -> this[languageLevel] = installer to release }
      }
    }
  }

  fun install(installableRelease: Pair<ReleaseInstaller, Release>, project: Project?, indicator: ProgressIndicator) {
    val (installer, release) = installableRelease
    installer.install(release, indicator) {
      PySdkToInstallCollector.logSdkDownload(
        project, release.version.toString(), PySdkToInstallCollector.DownloadResult.OK
      )
    }
    PySdkToInstallCollector.logSdkInstall(
      project, release.version.toString(), PySdkToInstallCollector.InstallationResult.OK
    )
  }

  fun install(sdk: PySdkToInstall, project: Project?, indicator: ProgressIndicator) {
    install(sdk.installer to sdk.release, project, indicator)
  }

  @RequiresEdt
  fun install(sdk: PySdkToInstall, module: Module?, systemWideSdksDetector: () -> List<PyDetectedSdk>): PyDetectedSdk? {
    val project = module?.project
    try {
      return ProgressManager.getInstance().run(
        object : Task.WithResult<PyDetectedSdk?, Exception>(project, PyBundle.message("python.sdk.installing", sdk.name), true) {
          override fun compute(indicator: ProgressIndicator): PyDetectedSdk? {
            install(sdk, project, indicator)
            return findInstalledSdk(sdk.release.version.toLanguageLevel(), project, systemWideSdksDetector)
          }
        }
      )
    }
    catch (ex: ReleaseInstallerException) {
      LOGGER.info(ex)
      PySdkToInstallCollector.logInstallerException(project, sdk.release, ex)
      showErrorNotification(project, ex)
    }
    return null
  }

  private fun showErrorNotification(project: Project?, ex: ReleaseInstallerException) {
    val title  = when (ex) {
      is PrepareException -> PyBundle.message("python.sdk.download.failed.title")
      else -> PyBundle.message("python.sdk.installation.failed.title")
    }

    val message = when (ex) {
      is CancelledProcessException, is CancelledPrepareException -> PyBundle.message("python.sdk.installation.cancelled.message")
      is PrepareException -> PyBundle.message("python.sdk.download.failed.message")
      else -> PyBundle.message("python.sdk.try.to.install.python.manually")
    }
    Messages.showErrorDialog(project, message, title)
  }


  private fun getLatestReleases(installer: ReleaseInstaller, product: Product = Product.CPython): Map<LanguageLevel, Release> {
    return SdksKeeper.pythonReleasesByLanguageLevel().mapNotNull { (langVersion, releases) ->
      releases.filter { it.product == product && installer.canInstall(it) }.maxOrNull()?.let { latest ->
        langVersion to latest
      }
    }.toMap()
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