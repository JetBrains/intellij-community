// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.export

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownNotifier
import org.intellij.plugins.markdown.fileActions.MarkdownFileActionFormat
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.settings.pandoc.PandocExecutableDetector
import java.util.*

internal class MarkdownDocxExportProvider : MarkdownExportProvider {
  override val formatDescription: MarkdownFileActionFormat
    get() = format

  override fun exportFile(project: Project, mdFile: VirtualFile, outputFile: String) {
    MarkdownExportDocxTask(project, mdFile, outputFile).queue()
  }

  override fun validate(project: Project, file: VirtualFile): String? {
    return when {
      PandocExecutableDetector.detect().isEmpty() -> MarkdownBundle.message("markdown.export.to.docx.failure.msg")
      else -> null
    }
  }

  private inner class MarkdownExportDocxTask(
    project: Project,
    private val mdFile: VirtualFile,
    private val outputFile: String
  ) : Task.Modal(project, MarkdownBundle.message("markdown.export.task", formatDescription.formatName), true) {
    private lateinit var output: ProcessOutput

    override fun run(indicator: ProgressIndicator) {
      //Note: if the reference document is not found for some reason, then all styles in the created docx document will be default.
      val refDocx = FileUtil.join(mdFile.parent.path, "${mdFile.nameWithoutExtension}.${formatDescription.extension}")
      val cmd = getConvertMdToDocxCommandLine(mdFile, outputFile, refDocx)

      output = ExecUtil.execAndGetOutput(cmd)
    }

    override fun onThrowable(error: Throwable) {
      MarkdownNotifier.showErrorNotification(project, "[${mdFile.name}] ${error.localizedMessage}")
    }

    override fun onSuccess() {
      if (output.stderrLines.isEmpty()) {
        MarkdownImportExportUtils.refreshProjectDirectory(project, mdFile.parent.path)
        MarkdownNotifier.showInfoNotification(
          project,
          MarkdownBundle.message("markdown.export.success.msg", mdFile.name)
        )
      }
      else {
        MarkdownNotifier.showErrorNotification(project, "[${mdFile.name}] ${output.stderrLines.joinToString("\n")}")
      }
    }

    private fun getConvertMdToDocxCommandLine(srcFile: VirtualFile, targetFile: String, refFile: String): GeneralCommandLine {
      val commandLine = mutableListOf(
        "pandoc",
        srcFile.path,
        "-f",
        MarkdownFileType.INSTANCE.name.lowercase(Locale.getDefault()),
        "-t",
        formatDescription.extension,
        "-o",
        targetFile
      )
      if (FileUtil.exists(refFile)) {
        commandLine.add("--reference-doc=$refFile")
      }
      return GeneralCommandLine(commandLine)
    }
  }

  companion object {
    @JvmStatic
    val format = MarkdownFileActionFormat("Microsoft Word", "docx")
  }
}
