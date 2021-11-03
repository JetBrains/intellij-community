// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.layout.*
import com.intellij.util.ModalityUiUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownNotifier
import org.intellij.plugins.markdown.fileActions.export.MarkdownDocxExportProvider
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.settings.pandoc.PandocSettings
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.jcef.JCEFHtmlPanelProvider
import java.io.File

/**
 * Utilities used mainly for import/export from markdown.
 */
object MarkdownImportExportUtils {
  /**
   * Returns the preview of markdown file or null if the preview editor or project is null.
   */
  fun getPreviewEditor(event: AnActionEvent, fileType: String): MarkdownPreviewFileEditor? {
    val project = event.project ?: return null

    val previewEditor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
    if (previewEditor == null) {
      MarkdownNotifier.showErrorNotification(
        project,
        MarkdownBundle.message("markdown.export.validation.failure.msg", fileType)
      )
      return null
    }

    return previewEditor
  }

  /**
   * recursively refreshes the specified directory in the project tree,
   * if the directory is not specified, the base directory of the project is refreshed.
   */
  fun refreshProjectDirectory(project: Project, refreshPath: String) {
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState()) {
      LocalFileSystem
        .getInstance()
        .refreshAndFindFileByIoFile(File(refreshPath))
        ?.refresh(true, true)
    }
  }

  /**
   * suggests a minimally conflicting name when importing a file,
   * checking for the existence of both docx and markdown files.
   */
  fun suggestFileNameToCreate(project: Project, fileToImport: VirtualFile, dataContext: DataContext): String {
    val defaultFileName = fileToImport.nameWithoutExtension
    val dirToImport = when (val selectedVirtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)) {
      null -> File(project.basePath!!)
      is VirtualFileImpl -> File(selectedVirtualFile.parent.path)
      else -> File(selectedVirtualFile.path)
    }

    val suggestMdFile = FileUtil.createSequentFileName(dirToImport, defaultFileName, MarkdownFileType.INSTANCE.defaultExtension)
    val suggestFileName = File(suggestMdFile).nameWithoutExtension
    val suggestDocxFile = FileUtil.createSequentFileName(dirToImport, suggestFileName, MarkdownDocxExportProvider.format.extension)

    return FileUtil.join(dirToImport.path, suggestDocxFile)
  }

  /**
   * converts the specified docx file using the pandoc utility,
   * and also calls the copy method for it to the same directory if the conversion was successful.
   */
  fun copyAndConvertToMd(project: Project,
                         vFileToImport: VirtualFile,
                         selectedFileUrl: String,
                         @NlsContexts.DialogTitle taskTitle: String) {
    object : Task.Modal(project, taskTitle, true) {
      private lateinit var createdFilePath: String
      private lateinit var output: ProcessOutput

      private val dirToImport = File(selectedFileUrl).parent
      private val newFileName = File(selectedFileUrl).nameWithoutExtension
      private val resourcesDir = PandocSettings.getInstance(project).pathToImages ?: project.basePath!!

      override fun run(indicator: ProgressIndicator) {
        val filePath = FileUtil.join(dirToImport, "${newFileName}.${MarkdownFileType.INSTANCE.defaultExtension}")
        val cmd = getConvertDocxToMdCommandLine(vFileToImport, resourcesDir, filePath)

        output = ExecUtil.execAndGetOutput(cmd)
        createdFilePath = filePath
      }

      override fun onCancel() {
        val mdFile = File(createdFilePath)
        if (mdFile.exists()) FileUtil.delete(mdFile)
      }

      override fun onThrowable(error: Throwable) {
        MarkdownNotifier.showErrorNotification(project, "[${vFileToImport.name}] ${error.localizedMessage}")
      }

      override fun onSuccess() {
        if (output.stderrLines.isEmpty()) {
          OpenFileAction.openFile(createdFilePath, project)
        }
        else {
          MarkdownNotifier.showErrorNotification(project, "[${vFileToImport.name}] ${output.stderrLines.joinToString("\n")}")
        }
      }
    }.queue()
  }

  private const val TARGET_FORMAT_NAME = "markdown"

  /**
   * returns a platform-independent cmd to perform the converting of docx to markdown using pandoc.
   */
  private fun getConvertDocxToMdCommandLine(file: VirtualFile, mediaSrc: String, targetFile: String) = GeneralCommandLine(
    "pandoc",
    "--extract-media=$mediaSrc",
    file.path,
    "-f",
    MarkdownDocxExportProvider.format.extension,
    "-t",
    TARGET_FORMAT_NAME,
    "-s",
    "-o",
    targetFile
  )

  /**
   * Checks whether the JCEF panel, which is needed for exporting to HTML and PDF, is open in the markdown editor.
   */
  fun isJCEFPanelOpen(editor: MarkdownPreviewFileEditor): Boolean {
    return editor.lastPanelProviderInfo?.className == JCEFHtmlPanelProvider::class.java.name
  }

  /**
   * Checks the directory selection field and returns an error if it is not filled in.
   */
  fun ValidationInfoBuilder.validateTargetDir(field: TextFieldWithHistoryWithBrowseButton): ValidationInfo? {
    return when {
      field.childComponent.text.isNullOrEmpty() -> error(RefactoringBundle.message("no.target.directory.specified"))
      else -> null
    }
  }

  fun notifyAndRefreshIfExportSuccess(file: File, project: Project) {
    MarkdownNotifier.showInfoNotification(
      project,
      MarkdownBundle.message("markdown.export.success.msg", file.name)
    )

    val dirToExport = file.parent
    refreshProjectDirectory(project, dirToExport)
  }
}

