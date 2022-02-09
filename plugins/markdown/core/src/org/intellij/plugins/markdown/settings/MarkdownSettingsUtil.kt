// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.intellij.util.download.DownloadableFileService
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithDownloadableFiles
import org.intellij.plugins.markdown.extensions.ExtensionsExternalFilesPathManager
import org.intellij.plugins.markdown.extensions.ExtensionsExternalFilesPathManager.Companion.obtainExternalFilesDirectoryPath
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import kotlin.io.path.absolutePathString

@ApiStatus.Internal
object MarkdownSettingsUtil {
  fun downloadExtensionFiles(
    extension: MarkdownExtensionWithDownloadableFiles,
    project: Project? = null,
    parentComponent: JComponent? = null
  ): Boolean {
    val downloader = DownloadableFileService.getInstance()
    val descriptions = extension.filesToDownload.mapNotNull { entry ->
      entry.link.get()?.let { downloader.createFileDescription(it, entry.filePath) }
    }
    ExtensionsExternalFilesPathManager.getInstance().cleanupExternalFiles(extension)
    val directory = extension.obtainExternalFilesDirectoryPath()
    val actualDownloader = downloader.createDownloader(descriptions, "Downloading Extension Files")
    val files = actualDownloader.downloadFilesWithProgress(directory.absolutePathString(), project, parentComponent)
    return files != null
  }

  fun downloadExtension(
    extension: MarkdownExtensionWithDownloadableFiles,
    project: Project? = null,
    enableAfterDownload: Boolean = false
  ): Boolean {
    if (extension.downloadFiles(project)) {
      if (enableAfterDownload) {
        MarkdownExtensionsSettings.getInstance().extensionsEnabledState[extension.id] = true
        application.messageBus.syncPublisher(MarkdownExtensionsSettings.ChangeListener.TOPIC).extensionsSettingsChanged(fromSettingsDialog = false)
      }
      Notifications.Bus.notify(
        Notification(
          "Markdown",
          MarkdownBundle.message("markdown.settings.download.extension.notification.title"),
          MarkdownBundle.message("markdown.settings.download.extension.notification.success.content"),
          NotificationType.INFORMATION
        )
      )
      return true
    } else {
      Notifications.Bus.notify(
        Notification(
          "Markdown",
          MarkdownBundle.message("markdown.settings.download.extension.notification.title"),
          MarkdownBundle.message("markdown.settings.download.extension.notification.failure.content"),
          NotificationType.ERROR
        )
      )
      return false
    }
  }
}
