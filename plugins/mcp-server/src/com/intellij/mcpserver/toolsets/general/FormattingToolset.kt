@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.nio.file.Path

class FormattingToolset : McpToolset {
  @McpTool
  @McpDescription("""
        |Reformats the specified files in the JetBrains IDE.
        |Use this tool to apply code formatting rules to files identified by their project-relative paths.
  """)
  suspend fun reformat_file(
    @McpDescription("List of project-relative files to reformat. Duplicate paths are ignored after normalization.")
    files: List<String>,
  ): String {
    val context = currentCoroutineContext()
    val project = context.project
    val requestedFiles = prepareRequestedFormattingFiles(project, files)
    context.reportToolActivity(McpServerBundle.message("tool.activity.formatting.files", requestedFiles.size))
    val commandName: @NlsContexts.Command String = reformatCommandName(requestedFiles)

    val psiFiles = Array(requestedFiles.size) { requestedFiles[it].psiFile }
    val codeProcessor = ReformatCodeProcessor(project, psiFiles, commandName, null, false)
    withContext(Dispatchers.EDT) {
      codeProcessor.run()
    }
    saveDocuments(project, requestedFiles, commandName)
    return "ok"
  }
}

private suspend fun saveDocuments(
  project: Project,
  requestedFiles: List<RequestedFormattingFile>,
  commandName: @NlsContexts.Command String,
) {
  val fileDocumentManager = FileDocumentManager.getInstance()
  val documents = readAction {
    requestedFiles.mapNotNull { fileDocumentManager.getDocument(it.virtualFile) }
  }
  if (documents.isNotEmpty()) {
    writeCommandAction(project, commandName) {
      val psiDocumentManager = PsiDocumentManager.getInstance(project)
      for (document in documents) {
        psiDocumentManager.commitDocument(document)
      }
    }

    // We need to save the changes on disk, otherwise bash tools and agent's read tool will be unable to read them immediately.
    for (document in documents) {
      fileDocumentManager.saveDocument(document)
    }
  }
}

private fun reformatCommandName(requestedFiles: List<RequestedFormattingFile>): @NlsContexts.Command String {
  return when (requestedFiles.size) {
    1 -> McpServerBundle.message("command.action.reformat.file", requestedFiles.single().path)
    else -> McpServerBundle.message("command.action.reformat.files", requestedFiles.size)
  }
}

private suspend fun prepareRequestedFormattingFiles(
  project: Project,
  files: List<String>,
): List<RequestedFormattingFile> {
  if (files.isEmpty()) {
    mcpFail("files must contain at least one path")
  }

  val requestedFiles = LinkedHashMap<Path, String>()
  val projectDirectory = project.projectDirectory
  files.forEach { addRequestedFormattingFile(requestedFiles, projectDirectory, it) }

  val localFileSystem = LocalFileSystem.getInstance()
  val psiManager = PsiManager.getInstance(project)
  return requestedFiles.map { (resolvedPath, path) ->
    val file = localFileSystem.refreshAndFindFileByNioFile(resolvedPath)
               ?: mcpFail("File $resolvedPath doesn't exist or can't be opened")
    val psiFile = readAction { psiManager.findFile(file) }
                  ?: mcpFail("File $file doesn't exist or can't be opened")
    RequestedFormattingFile(path, file, psiFile)
  }
}

private fun addRequestedFormattingFile(requestedFiles: MutableMap<Path, String>, projectDirectory: Path, rawPath: String?) {
  if (rawPath == null) return

  val path = rawPath.trim().ifEmpty { mcpFail("files must not contain blank paths") }
  val resolvedPath = resolveExistingRegularFileInProject(pathInProject = path, projectDirectory = projectDirectory)
  requestedFiles.putIfAbsent(resolvedPath, path)
}


private class RequestedFormattingFile(
  @JvmField val path: String,
  @JvmField val virtualFile: VirtualFile,
  @JvmField val psiFile: PsiFile,
)