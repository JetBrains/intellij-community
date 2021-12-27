package org.intellij.plugins.markdown.extensions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.runModalTask
import org.intellij.plugins.markdown.MarkdownBundle

internal class CleanupExtensionsExternalFilesAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    runModalTask(MarkdownBundle.message("Markdown.Extensions.CleanupExternalFiles.task.title"), event.project, cancellable = false) {
      val extensions = MarkdownExtensionsUtil.collectExtensionsWithExternalFiles()
      val pathManager = ExtensionsExternalFilesPathManager.getInstance()
      for (extension in extensions) {
        pathManager.cleanupExternalFiles(extension)
      }
      Notifications.Bus.notify(
        Notification(
          "Markdown",
          MarkdownBundle.message("Markdown.Extensions.CleanupExternalFiles.notification.title"),
          MarkdownBundle.message("Markdown.Extensions.CleanupExternalFiles.notification.text"),
          NotificationType.INFORMATION
        )
      )
    }
  }
}
