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
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

class FormattingToolset : McpToolset {
  @McpTool
  @McpDescription("""
        |Reformats the specified files in the JetBrains IDE.
        |Use this tool to apply code formatting rules to files identified by their project-relative paths.
  """)
  suspend fun reformat_file(
    @McpDescription("Project-relative file path to reformat. Deprecated: prefer `paths` for batch formatting.")
    path: String? = null,
    @McpDescription("List of project-relative file paths to reformat. Duplicate paths are ignored after normalization.")
    paths: List<String>? = null,
  ): String {
    val context = currentCoroutineContext()
    val project = context.project
    val requestedFiles = prepareRequestedFormattingFiles(project, path, paths)
    context.reportToolActivity(McpServerBundle.message("tool.activity.formatting.files", requestedFiles.size))
    val commandName: @NlsContexts.Command String = reformatCommandName(requestedFiles)

    val finished = CompletableDeferred<Unit>()
    val codeProcessor =
      ReformatCodeProcessor(project, requestedFiles.map { it.psiFile }.toTypedArray(), commandName, { finished.complete(Unit) }, false)
    withContext(Dispatchers.EDT) {
      codeProcessor.run()
    }
    finished.await()
    saveDocuments(project, requestedFiles, commandName)
    return "ok"
  }

  private suspend fun saveDocuments(
    project: Project,
    requestedFiles: List<RequestedFormattingFile>,
    commandName: @NlsContexts.Command String,
  ) {
    val documents = readAction {
      requestedFiles.mapNotNull { FileDocumentManager.getInstance().getDocument(it.virtualFile) }
    }
    if (documents.isNotEmpty()) {
      // We need to save the changes on disk, otherwise bash tools and agent's read tool will be unable to read them immediately.
      writeCommandAction(project, commandName) {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val fileDocumentManager = FileDocumentManager.getInstance()
        for (document in documents) {
          psiDocumentManager.commitDocument(document)
          fileDocumentManager.saveDocument(document)
        }
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
    path: String?,
    paths: List<String>?,
  ): List<RequestedFormattingFile> {
    val requestedFiles = LinkedHashMap<Path, String>()
    addRequestedFormattingFile(requestedFiles, project.projectDirectory, path)
    paths?.forEach { addRequestedFormattingFile(requestedFiles, project.projectDirectory, it) }

    if (requestedFiles.isEmpty()) {
      mcpFail("path or paths must contain at least one path")
    }

    val psiManager = PsiManager.getInstance(project)
    return requestedFiles.map { (resolvedPath, path) ->
      val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
                 ?: mcpFail("File $resolvedPath doesn't exist or can't be opened")
      val psiFile = readAction { psiManager.findFile(file) }
                    ?: mcpFail("File $file doesn't exist or can't be opened")
      RequestedFormattingFile(path, file, psiFile)
    }
  }

  private fun addRequestedFormattingFile(requestedFiles: MutableMap<Path, String>, projectDirectory: Path, rawPath: String?) {
    if (rawPath == null) return

    val path = rawPath.trim().ifEmpty { mcpFail("paths must not contain blank paths") }
    val resolvedPath = resolveInProject(pathInProject = path, projectDirectory = projectDirectory)
    if (Files.notExists(resolvedPath)) {
      mcpFail("File not found: $path")
    }
    if (!resolvedPath.isRegularFile()) {
      mcpFail("Not a file: $path")
    }
    requestedFiles.putIfAbsent(resolvedPath, path)
  }

  private class RequestedFormattingFile(
    val path: String,
    val virtualFile: VirtualFile,
    val psiFile: PsiFile,
  )
}