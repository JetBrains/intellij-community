// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.jcef.fileActions.export

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.markdown.jcef.preview.JCEFHtmlPanelProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.MarkdownFileActionFormat
import org.intellij.plugins.markdown.fileActions.export.MarkdownExportProvider
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils.notifyExportResult
import org.intellij.plugins.markdown.util.MarkdownPluginScope
import java.io.File

internal class MarkdownPdfExportProvider : MarkdownExportProvider {
  override val formatDescription: MarkdownFileActionFormat
    get() = format

  override fun exportFile(project: Project, mdFile: VirtualFile, outputFile: String) {
    MarkdownPluginScope.scope(project).launch {
      val isSuccessful = withBackgroundProgress(project, MarkdownBundle.message("markdown.export.task", formatDescription.formatName)) {
        try {
          printMarkdownToPdf(project, mdFile, outputFile)
        }
        catch (e: Exception) {
          rethrowControlFlowException(e)
          logger<MarkdownPdfExportProvider>().warn("Cannot export ${mdFile.path} to PDF", e)
          false
        }
      }
      notifyExportResult(project, File(outputFile), isSuccessful)
    }
  }

  override fun validate(project: Project, file: VirtualFile): String? {
    if (!JCEFHtmlPanelProvider.canBeUsedForExport()) {
      return MarkdownBundle.message("markdown.export.validation.failure.msg", formatDescription.formatName)
    }
    return null
  }

  companion object {
    @JvmStatic
    val format = MarkdownFileActionFormat("PDF", "pdf")
  }
}
