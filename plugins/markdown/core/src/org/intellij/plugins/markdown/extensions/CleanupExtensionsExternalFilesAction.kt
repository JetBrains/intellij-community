package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.runModalTask

internal class CleanupExtensionsExternalFilesAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    runModalTask("Removing Extensions External Files", event.project, cancellable = false) {
      val extensions = MarkdownExtensionsUtil.collectExtensionsWithExternalFiles()
      val pathManager = ExtensionsExternalFilesPathManager.getInstance()
      for (extension in extensions) {
        pathManager.cleanupExternalFiles(extension)
      }
    }
  }
}
