// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.installer

import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.HttpRequests
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.sdk.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.fileSize

val LOGGER = logger<ReleaseInstaller>()

data class ReleasePreview(val description: String, val size: Long) {
  companion object {
    fun of(resource: Resource?) = resource?.let { ReleasePreview(it.url.toString(), it.size) } ?: ReleasePreview("", 0)
  }
}

/**
 * Software Release Installer.
 *
 * @see com.jetbrains.python.sdk.Release
 */
interface ReleaseInstaller {
  /**
   * Verifies if it can install the release (have enough binary/source resources or tools installed).
   */
  fun canInstall(release: Release): Boolean

  /**
   * Installation process of the selected software release.
   * Should be checked with canInstall, otherwise the behavior is not predictable.
   * @see ReleaseInstaller.canInstall
   * @param onPrepareComplete callback function, have to be called right before the installation process.
   */
  @Throws(ReleaseInstallerException::class)
  fun install(release: Release, indicator: ProgressIndicator, onPrepareComplete: () -> Unit)

  /**
   * Preview for pre-install messages and warnings.
   */
  fun getPreview(release: Release): ReleasePreview
}

/**
 * Base Release Installer with additional external resource loading.
 * Responsible for loading and checking the checksum/size of additional resources before processing.
 */
abstract class DownloadableReleaseInstaller : ReleaseInstaller {
  /**
   * Concrete installer should choose which resources it needs.
   * Might be any additional resources or tools outside the release scope.
   */
  abstract fun getResourcesToDownload(release: Release): List<Resource>

  /**
   * Concrete installer process entry point.
   * There is a guarantee that at this moment all requested resources were downloaded
   * and their paths are stored in the resourcePaths.
   *
   * @see getResourcesToDownload
   * @param resourcePaths - requested resources download paths (on the temp path)
   */
  @Throws(ProcessException::class)
  abstract fun process(release: Release, resourcePaths: Map<Resource, Path>, indicator: ProgressIndicator)

  @Throws(ReleaseInstallerException::class)
  override fun install(release: Release, indicator: ProgressIndicator, onPrepareComplete: () -> Unit) {
    val tempPath = PathManager.getTempPath()
    val files = getResourcesToDownload(release).associateWith {
      Paths.get(tempPath, "${System.nanoTime()}-${it.fileName}")
    }
    try {
      indicator.text = PySdkBundle.message("python.sdk.preparation.progress.text")
      files.forEach { (resource, path) ->
        download(resource, path, indicator)
      }

      onPrepareComplete()

      process(release, files, indicator)
    }
    finally {
      files.values.forEach { runCatching { FileUtil.delete(it) } }
    }
  }

  @Throws(PrepareException::class)
  private fun checkConsistency(resource: Resource, target: Path) {
    LOGGER.debug("Checking installer size")
    val sizeDiff = target.fileSize() - resource.size
    if (sizeDiff != 0L) {
      throw WrongSizePrepareException(target, sizeDiff)
    }

    LOGGER.debug("Checking installer checksum")
    val actualHashCode = Files.asByteSource(target.toFile()).hash(Hashing.sha256()).toString()
    if (!resource.sha256.equals(actualHashCode, ignoreCase = true)) {
      throw WrongChecksumPrepareException(target, resource.sha256, actualHashCode)
    }
  }

  @Throws(PrepareException::class)
  private fun download(resource: Resource, target: Path, indicator: ProgressIndicator) {
    LOGGER.info("Downloading ${resource.url} to $target")
    indicator.text2 = PySdkBundle.message("python.sdk.downloading.progress.details", resource.fileName)
    try {
      indicator.checkCanceled()
      HttpRequests.request(resource.url)
        .productNameAsUserAgent()
        .saveToFile(target.toFile(), indicator)

      indicator.checkCanceled()
      checkConsistency(resource, target)
    }
    catch (e: ProcessCanceledException) {
      throw CancelledPrepareException(e)
    }
    catch (e: PrepareException) {
      throw e
    }
    catch (e: Exception) {
      throw PrepareException(e)
    }
  }

  override fun getPreview(release: Release): ReleasePreview {
    return ReleasePreview.of(getResourcesToDownload(release).firstOrNull())
  }
}
