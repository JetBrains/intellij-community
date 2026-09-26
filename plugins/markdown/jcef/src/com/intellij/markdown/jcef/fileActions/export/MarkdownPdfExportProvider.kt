// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.jcef.fileActions.export

import com.intellij.markdown.jcef.preview.JCEFHtmlPanelProvider
import com.intellij.markdown.jcef.preview.MarkdownJCEFHtmlPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.cef.misc.CefPdfPrintSettings
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.MarkdownFileActionFormat
import org.intellij.plugins.markdown.fileActions.export.MarkdownExportProvider
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils.notifyAndRefreshIfExportSuccess
import org.intellij.plugins.markdown.ui.MarkdownNotifications
import java.io.File
import java.util.function.BiConsumer

internal class MarkdownPdfExportProvider : MarkdownExportProvider {
  override val formatDescription: MarkdownFileActionFormat
    get() = format

  override fun exportFile(project: Project, mdFile: VirtualFile, outputFile: String) {
    withMarkdownPreview(project, mdFile) { htmlPanel, onFinished ->
      htmlPanel.savePdf(outputFile, project, onFinished) { path, ok ->
        if (ok) {
          val file = VfsUtil.findFileByIoFile(File(path), true)
          if (file != null) {
            notifyAndRefreshIfExportSuccess(file, project)
          }
        }
        else {
          MarkdownNotifications.showError(
            project,
            id = MarkdownExportProvider.Companion.NotificationIds.exportFailed,
            message = MarkdownBundle.message("markdown.export.failure.msg", File(path).name)
          )
        }
      }
    }
  }

  override fun validate(project: Project, file: VirtualFile): String? {
    if (!JCEFHtmlPanelProvider.canBeUsed()) {
      return MarkdownBundle.message("markdown.export.validation.failure.msg", formatDescription.formatName)
    }
    return null
  }

  private fun MarkdownJCEFHtmlPanel.savePdf(path: String, project: Project, onFinished: () -> Unit, callback: BiConsumer<String, Boolean>) {
    cefBrowser.printToPDF(path, CefPdfPrintSettings()) { _, ok ->
      try {
        val dirToExport = VfsUtil.findFileByIoFile(File(path), true)?.parent
        if (dirToExport != null) {
          MarkdownImportExportUtils.refreshProjectDirectory(project, dirToExport.path)
        }

        callback.accept(path, ok)
      }
      finally {
        onFinished()
      }
    }
  }

  companion object {
    @JvmStatic
    val format = MarkdownFileActionFormat("PDF", "pdf")
  }
}
