package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.runModalTask
import com.intellij.util.application
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.intellij.plugins.markdown.ui.MarkdownNotifications

internal class CleanupExtensionsExternalFilesAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    runModalTask(MarkdownBundle.message("Markdown.Extensions.CleanupExternalFiles.task.title"), event.project, cancellable = false) {
      val extensions = MarkdownExtensionsUtil.collectExtensionsWithExternalFiles()
      val pathManager = ExtensionsExternalFilesPathManager.getInstance()
      for (extension in extensions) {
        pathManager.cleanupExternalFiles(extension)
      }
      MarkdownExtensionsSettings.getInstance().extensionsEnabledState.clear()
      val publisher = application.messageBus.syncPublisher(MarkdownExtensionsSettings.ChangeListener.TOPIC)
      publisher.extensionsSettingsChanged(fromSettingsDialog = false)
      MarkdownNotifications.showInfo(
        project = event.project,
        id = "markdown.extensions.external.files.cleanup",
        title = MarkdownBundle.message("Markdown.Extensions.CleanupExternalFiles.notification.title"),
        message = MarkdownBundle.message("Markdown.Extensions.CleanupExternalFiles.notification.text"),
      )
    }
  }
}
