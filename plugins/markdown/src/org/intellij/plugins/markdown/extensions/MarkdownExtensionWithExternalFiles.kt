// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.application.PathManager
import org.jetbrains.annotations.TestOnly
import java.io.File

/**
 * Extensions which require additional files to work should implement
 * this interface. If [downloadLink] is not null, users will be prompted
 * with download dialog on the first usage.
 */
interface MarkdownExtensionWithExternalFiles : MarkdownConfigurableExtension {
  /**
   * Set to null if extension doesn't need to download any files.
   * Also, be sure to set available to true in that case.
   */
  val downloadLink: String?

  /**
   * Downloaded file name.
   */
  val downloadFilename: String

  /**
   * Is true if extension files are available (e.g. downloaded).
   */
  val isAvailable: Boolean
    get() = directory.exists()

  override val isEnabled: Boolean
    get() = super.isEnabled && isAvailable

  /**
   * Directory name inside [extensionsDownloadDirectory] for downloaded file.
   */
  // Apply lowercase because of macos case insensitive pathing
  val directoryName: String
    get() = displayName.replace(' ', '_').toLowerCase()

  /**
   * Full path to the extension directory.
   */
  val directory: File
    get() = File(extensionsDownloadDirectory, directoryName)

  /**
   * Full path to downloaded file.
   */
  val fullPath: File
    get() = File(directory, downloadFilename)

  /**
   * This method will be called when [downloadFilename] have finished downloading.
   * Use this method for any post processing (e.g. unzip)
   *
   * @return true, if operations completed successfully, false in case of any failures.
   */
  fun afterDownload(): Boolean = true

  companion object {
    @JvmStatic
    val downloadCacheDirectoryName = "download-cache"

    // @TestOnly does not work with kotlin
    @TestOnly
    @JvmStatic
    var BASE_DIRECTORY = PathManager.getSystemPath()

    @JvmStatic
    val extensionsDownloadDirectory
      get() = File(BASE_DIRECTORY, downloadCacheDirectoryName)
  }
}
