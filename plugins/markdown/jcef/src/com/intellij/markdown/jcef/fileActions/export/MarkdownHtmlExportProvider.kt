// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.jcef.fileActions.export

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.markdown.jcef.preview.JCEFHtmlPanelProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.export.HtmlResourceSavingSettings
import org.intellij.plugins.markdown.fileActions.export.MarkdownHtmlExportProviderBase
import org.intellij.plugins.markdown.fileActions.export.MarkdownHtmlExportResult
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils.notifyExportResult
import org.intellij.plugins.markdown.util.MarkdownPluginScope
import java.io.File

internal class MarkdownHtmlExportProvider : MarkdownHtmlExportProviderBase() {
  override fun exportFile(project: Project, mdFile: VirtualFile, outputFile: String, savingSettings: HtmlResourceSavingSettings) {
    val targetFile = File(outputFile)
    val imagesSrcDir = if (savingSettings.isSaved) MarkdownHtmlExportResult.getImagesSrcDir(targetFile, savingSettings.resourceDir) else null
    MarkdownPluginScope.scope(project).launch {
      val isSuccessful = withBackgroundProgress(project, MarkdownBundle.message("markdown.export.task", formatDescription.formatName)) {
        try {
          val result = exportMarkdownToHtml(project, mdFile, imagesSrcDir)
          withContext(Dispatchers.IO) {
            result.writeTo(targetFile, savingSettings.resourceDir)
          }
          true
        }
        catch (e: Exception) {
          rethrowControlFlowException(e)
          logger<MarkdownHtmlExportProvider>().warn("Cannot export ${mdFile.path} to HTML", e)
          false
        }
      }
      notifyExportResult(project, targetFile, isSuccessful)
    }
  }

  override fun validate(project: Project, file: VirtualFile): String? {
    if (!JCEFHtmlPanelProvider.canBeUsedForExport()) {
      return MarkdownBundle.message("markdown.export.validation.failure.msg", formatDescription.formatName)
    }
    return null
  }
}
