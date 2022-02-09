// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.export

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.cef.misc.CefPdfPrintSettings
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownNotifier
import org.intellij.plugins.markdown.fileActions.MarkdownFileActionFormat
import org.intellij.plugins.markdown.fileActions.utils.MarkdownFileEditorUtils
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils.notifyAndRefreshIfExportSuccess
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import java.io.File
import java.util.function.BiConsumer

internal class MarkdownPdfExportProvider : MarkdownExportProvider {
  override val formatDescription: MarkdownFileActionFormat
    get() = format

  override fun exportFile(project: Project, mdFile: VirtualFile, outputFile: String) {
    val preview = MarkdownFileEditorUtils.findMarkdownPreviewEditor(project, mdFile, true) ?: return
    val htmlPanel = preview.getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER) ?: return

    if (htmlPanel is MarkdownJCEFHtmlPanel) {
      htmlPanel.savePdf(outputFile, project) { path, ok ->
        if (ok) {
          notifyAndRefreshIfExportSuccess(File(path), project)
        }
        else {
          MarkdownNotifier.showErrorNotification(
            project,
            MarkdownBundle.message("markdown.export.failure.msg", File(path).name)
          )
        }
      }
    }
  }

  override fun validate(project: Project, file: VirtualFile): String? {
    val preview = MarkdownFileEditorUtils.findMarkdownPreviewEditor(project, file, true)
    if (preview == null || !MarkdownImportExportUtils.isJCEFPanelOpen(preview)) {
      return MarkdownBundle.message("markdown.export.validation.failure.msg", formatDescription.formatName)
    }
    return null
  }

  private fun MarkdownJCEFHtmlPanel.savePdf(path: String, project: Project, resultCallback: BiConsumer<String, Boolean>) {
    cefBrowser.printToPDF(path, CefPdfPrintSettings()) { _, ok ->
      val dirToExport = File(path).parent
      MarkdownImportExportUtils.refreshProjectDirectory(project, dirToExport)

      resultCallback.accept(path, ok)
    }
  }

  companion object {
    @JvmStatic
    val format = MarkdownFileActionFormat("PDF", "pdf")
  }
}
