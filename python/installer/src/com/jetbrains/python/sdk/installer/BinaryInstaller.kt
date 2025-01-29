// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.installer

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.python.community.impl.installer.BinaryInstallerUsagesCollector
import com.intellij.python.community.impl.installer.PyInstallerBundle
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.sdk.Binary
import com.jetbrains.python.sdk.Release
import com.jetbrains.python.sdk.Resource

// Symbols in this class aren't public API, do not use them!

internal val LOGGER: Logger = logger<BinaryInstaller>()

data class ResourcePreview(val description: String, val size: Long) {
  companion object {
    fun of(resource: Resource?): ResourcePreview = resource?.let { ResourcePreview(it.url.toString(), it.size) } ?: ResourcePreview("", 0)
  }
}

data class BinaryInstallation(val release: Release, val binary: Binary, val installer: BinaryInstaller)

fun BinaryInstallation.toResourcePreview(): ResourcePreview {
  return installer.getPreview(binary)
}

/**
 * Software Release Installer.
 *
 * @see Release
 */
interface BinaryInstaller {
  /**
   * Verifies if it can install the release (have enough binary/source resources or tools installed).
   */
  fun canInstall(binary: Binary): Boolean

  /**
   * Installation process of the selected software release.
   * Should be checked with canInstall, otherwise the behavior is not predictable.
   * @see BinaryInstaller.canInstall
   * @param onPrepareComplete callback function, have to be called right before the installation process.
   */
  @Throws(BinaryInstallerException::class)
  fun install(binary: Binary, indicator: ProgressIndicator, onPrepareComplete: () -> Unit)

  /**
   * Preview for pre-install messages and warnings.
   */
  fun getPreview(binary: Binary): ResourcePreview
}

internal fun Release.selectInstallations(installers: List<BinaryInstaller>): List<BinaryInstallation> {
  val compatibleBinaries = this.binaries?.filter { it.isCompatible() } ?: emptyList()
  return compatibleBinaries.mapNotNull { binary ->
    installers.firstOrNull { it.canInstall(binary) }?.let { BinaryInstallation(this, binary, it) }
  }
}

@RequiresEdt
fun <T> installBinary(installation: BinaryInstallation, project: Project?, postInstall: () -> T? = { null }): Result<T> {
  val (release, binary, installer) = installation
  try {
    return Result.success(ProgressManager.getInstance().run(
      object : Task.WithResult<T, Exception>(project, PyInstallerBundle.message("python.sdk.installing", release.title), true) {
        override fun compute(indicator: ProgressIndicator): T? {
          installer.install(binary, indicator) {
            BinaryInstallerUsagesCollector.logDownloadEvent(project, release, BinaryInstallerUsagesCollector.DownloadResult.OK)
          }
          BinaryInstallerUsagesCollector.logInstallationEvent(project, release, BinaryInstallerUsagesCollector.InstallationResult.OK)
          return postInstall()
        }
      }
    ))
  }
  catch (ex: BinaryInstallerException) {
    LOGGER.info(ex)
    BinaryInstallerUsagesCollector.logInstallerException(project, installation.release, ex)
    showErrorNotification(project, release, ex)
    return Result.failure(ex)
  }
}

private fun showErrorNotification(project: Project?, release: Release, ex: BinaryInstallerException) {
  val title = when (ex) {
    is PrepareException -> PyInstallerBundle.message("python.sdk.download.failed.title", release.title)
    else -> PyInstallerBundle.message("python.sdk.installation.failed.title", release.title)
  }

  val message = when (ex) {
    is CancelledProcessException, is CancelledPrepareException -> PyInstallerBundle.message("python.sdk.installation.cancelled.message")
    is PrepareException -> PyInstallerBundle.message("python.sdk.download.failed.message")
    else -> PyInstallerBundle.message("python.sdk.try.to.install.manually", release.title)
  }
  Messages.showErrorDialog(project, message, title)
}