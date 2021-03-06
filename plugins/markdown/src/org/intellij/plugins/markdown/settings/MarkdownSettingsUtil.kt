// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.util.download.DownloadableFileService
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import javax.swing.JComponent

internal object MarkdownSettingsUtil {
  fun downloadExtensionFiles(extension: MarkdownExtensionWithExternalFiles, parentComponent: JComponent? = null): Boolean {
    val downloader = DownloadableFileService.getInstance()
    val description = downloader.createFileDescription(
      extension.downloadLink ?: error("Could not download files with empty link!"),
      extension.downloadFilename
    )
    if (extension.directory.exists()) {
      extension.directory.delete()
    }
    val result = downloader.createDownloader(listOf(description), extension.downloadFilename)
      .downloadFilesWithProgress(
        extension.directory.absolutePath,
        null,
        parentComponent
      )?.also {
        extension.afterDownload()
      }
    return result != null
  }

  fun downloadExtension(extension: MarkdownExtensionWithExternalFiles) {
    if (downloadExtensionFiles(extension)) {
      Notifications.Bus.notify(
        Notification(
          "Markdown",
          MarkdownBundle.message("markdown.settings.download.extension.notification.title"),
          MarkdownBundle.message("markdown.settings.download.extension.notification.success.content"),
          NotificationType.INFORMATION
        )
      )
    }
    else {
      Notifications.Bus.notify(
        Notification(
          "Markdown",
          MarkdownBundle.message("markdown.settings.download.extension.notification.title"),
          MarkdownBundle.message("markdown.settings.download.extension.notification.failure.content"),
          NotificationType.ERROR
        )
      )
    }
  }
}
