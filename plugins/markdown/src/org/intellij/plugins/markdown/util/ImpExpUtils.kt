// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl
import com.intellij.psi.PsiManager
import com.intellij.util.PathUtil
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownNotifier
import org.intellij.plugins.markdown.fileActions.DocxFileType
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.settings.pandoc.PandocApplicationSettings
import java.io.File

object ImpExpUtils {
  private const val TARGET_FORMAT_NAME = "markdown"

  fun suggestFileNameToCreate(project: Project, fileToImport: VirtualFile, dataContext: DataContext): String {
    val defaultFileName = fileToImport.nameWithoutExtension
    val dirToImport = when (val selectedVirtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)) {
      null -> File(project.basePath!!)
      is VirtualFileImpl -> File(selectedVirtualFile.parent.path)
      else -> File(selectedVirtualFile.path)
    }

    val suggestMdFile = FileUtil.createSequentFileName(dirToImport, defaultFileName, MarkdownFileType.INSTANCE.defaultExtension)
    val suggestFileName = File(suggestMdFile).nameWithoutExtension
    val suggestDocxFile = FileUtil.createSequentFileName(dirToImport, suggestFileName, DocxFileType.INSTANCE.defaultExtension)

    return PathUtil.toSystemDependentName("$dirToImport/$suggestDocxFile")
  }

  fun copyAndConvertToMd(project: Project, vFileToImport: VirtualFile, selectedFileUrl: String) {
    object : Task.Modal(project, MarkdownBundle.message("markdown.import.docx.convert"), true) {
      private lateinit var createdFilePath: String
      private lateinit var output: ProcessOutput

      private val dirToImport = File(selectedFileUrl).parent
      private val newFileName = File(selectedFileUrl).nameWithoutExtension
      private val resourcesDir = PandocApplicationSettings.getInstance().state.myPathToImages ?: project.basePath!!

      override fun run(indicator: ProgressIndicator) {
        val filePath = PathUtil.toSystemDependentName("$dirToImport/${newFileName}.${MarkdownFileType.INSTANCE.defaultExtension}")
        val cmd = getConvertDocxToMdCommandLine(vFileToImport, resourcesDir, filePath)

        output = ExecUtil.execAndGetOutput(cmd)
        createdFilePath = filePath
      }

      override fun onCancel() {
        val mdFile = File(createdFilePath)
        if (mdFile.exists()) FileUtil.delete(mdFile)
      }

      override fun onThrowable(error: Throwable) {
        MarkdownNotifier.notifyIfConvertFailed(project, "[${vFileToImport.name}] ${error.localizedMessage}")
      }

      override fun onSuccess() {
        if (output.stderrLines.isEmpty()) {
          vFileToImport.copySelectedFile(project, dirToImport, newFileName)
          OpenFileAction.openFile(createdFilePath, project)
        }
        else {
          MarkdownNotifier.notifyIfConvertFailed(project, "[${vFileToImport.name}] ${output.stderrLines.joinToString("\n")}")
        }
      }
    }.queue()
  }

  private fun VirtualFile.copySelectedFile(project: Project, dirToImport: String, newFileName: String) {
    val fileNameWithExtension = "$newFileName.${DocxFileType.INSTANCE.defaultExtension}"

    try {
      val localFS = LocalFileSystem.getInstance()
      val dirToImportVF = localFS.findFileByPath(dirToImport) ?: localFS.findFileByPath(project.basePath!!)!!

      runWriteAction {
        PsiManager.getInstance(project)
          .findDirectory(dirToImportVF)!!
          .copyFileFrom(fileNameWithExtension, PsiManager.getInstance(project).findFile(this)!!)
      }
    }
    catch (e: Throwable) {
      MarkdownNotifier.notifyIfConvertFailed(project, "[$fileNameWithExtension] ${e.localizedMessage}")
    }
  }

  private fun getConvertDocxToMdCommandLine(file: VirtualFile, mediaSrc: String, targetFile: String) = GeneralCommandLine(
    "pandoc",
    "--extract-media=$mediaSrc",
    file.path,
    "-f",
    DocxFileType.INSTANCE.defaultExtension,
    "-t",
    TARGET_FORMAT_NAME,
    "-s",
    "-o",
    targetFile
  )
}
