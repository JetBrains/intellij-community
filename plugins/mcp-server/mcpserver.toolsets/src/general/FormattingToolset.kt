@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.util.awaitExternalChangesAndIndexing
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

class FormattingToolset : McpToolset {
  override fun displayName(): String = McpServerBundle.message("toolset.display.name.formatting")

  override fun displayDescription(toolName: String): String = McpServerBundle.message("tool.description.$toolName")

  @McpTool
  @McpDescription("""
        |Reformats the specified files in the JetBrains IDE.
        |Use this tool to apply code formatting rules to files identified by their project-relative paths.
        |Returns a unified diff of the changes that the formatter applied, or "ok" when the files already match the code style.
        |The diff shows the formatter changes only. It never repeats the edit that you applied yourself.
        |The diff stops at 200 lines. A footer names the file(s) that it leaves out.
        |To remove unused imports without reformatting, use optimize_imports.
  """)
  suspend fun reformat_file(
    @McpDescription("List of project-relative files to reformat. Duplicate paths are ignored after normalization.")
    files: List<String>,
    @McpDescription("If true, also remove unused imports and order the remaining ones before reformatting.")
    optimizeImports: Boolean = false,
  ): String {
    val context = currentCoroutineContext()
    val project = context.project
    val requestedFiles = prepareRequestedFormattingFiles(project, files)
    context.reportToolActivity(McpServerBundle.message("tool.activity.formatting.files", requestedFiles.size))
    val commandName: @NlsContexts.Command String = reformatCommandName(requestedFiles)

    // The snapshot is taken after the refresh, so the diff reports the formatter changes only.
    val textBefore = readDocumentTexts(requestedFiles)
    if (optimizeImports) {
      optimizeImports(project, requestedFiles)
    }
    val psiFiles = Array(requestedFiles.size) { requestedFiles[it].psiFile }
    val codeProcessor = ReformatCodeProcessor(project, psiFiles, commandName, null, false)
    withContext(Dispatchers.EDT) {
      codeProcessor.run()
    }
    saveDocuments(project, requestedFiles, commandName)
    return diffSince(requestedFiles, textBefore)
  }

  @McpTool
  @McpDescription("""
        |Optimizes imports in the specified files in the JetBrains IDE: removes unused imports and orders the remaining ones by the code style.
        |It never adds missing imports.
        |Returns a unified diff of the changes, or "ok" when nothing changed.
        |The diff stops at 200 lines. A footer names the file(s) that it leaves out.
  """)
  suspend fun optimize_imports(
    @McpDescription("List of project-relative files to optimize imports in. Duplicate paths are ignored after normalization.")
    files: List<String>,
  ): String {
    val context = currentCoroutineContext()
    val project = context.project
    val requestedFiles = prepareRequestedFormattingFiles(project, files)
    context.reportToolActivity(McpServerBundle.message("tool.activity.optimizing.imports", requestedFiles.size))

    val textBefore = readDocumentTexts(requestedFiles)
    optimizeImports(project, requestedFiles)
    saveDocuments(project, requestedFiles, McpServerBundle.message("command.action.optimize.imports", requestedFiles.size))
    return diffSince(requestedFiles, textBefore)
  }
}

private suspend fun optimizeImports(project: Project, requestedFiles: List<RequestedFormattingFile>) {
  val directories = requestedFiles.mapNotNull { it.virtualFile.parent }.distinct()
  RefreshQueue.getInstance().refresh(recursive = false, files = directories)
  awaitExternalChangesAndIndexing(project)
  val psiFiles = Array(requestedFiles.size) { requestedFiles[it].psiFile }
  val commandName = McpServerBundle.message("command.action.optimize.imports", requestedFiles.size)
  val processor = OptimizeImportsProcessor(project, psiFiles, commandName, null, false)
  withContext(Dispatchers.EDT) {
    if (DumbService.isDumb(project)) {
      mcpFail("The indexes were not ready, so the unused imports cannot be found. Nothing changed. Retry when indexing finishes.")
    }
    processor.run()
  }
}

private suspend fun diffSince(requestedFiles: List<RequestedFormattingFile>, textBefore: Map<VirtualFile, String>): String {
  val textAfter = readDocumentTexts(requestedFiles)
  return buildUnifiedDiff(requestedFiles.map { file ->
    ChangedFileText(file.path, textBefore[file.virtualFile], textAfter[file.virtualFile])
  }) ?: "ok"
}

private suspend fun readDocumentTexts(requestedFiles: List<RequestedFormattingFile>): Map<VirtualFile, String> {
  val fileDocumentManager = FileDocumentManager.getInstance()
  return readAction {
    requestedFiles.mapNotNull { file ->
      fileDocumentManager.getDocument(file.virtualFile)?.let { document -> file.virtualFile to document.text }
    }.toMap()
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

  val requestedFiles = LinkedHashMap<VirtualFile, String>()
  files.forEach { addRequestedFormattingFile(requestedFiles, project, it) }

  val psiManager = PsiManager.getInstance(project)
  // Agents write files directly to disk, often before the file watcher notices; without a forced refresh
  // the stale document is reformatted and saved over the agent's edit.
  RefreshQueue.getInstance().refresh(recursive = false, files = requestedFiles.keys.toList())
  return requestedFiles.map { (file, path) ->
    val psiFile = readAction { psiManager.findFile(file) }
                  ?: mcpFail("File $file doesn't exist or can't be opened")
    RequestedFormattingFile(path, file, psiFile)
  }
}

private fun addRequestedFormattingFile(requestedFiles: MutableMap<VirtualFile, String>, project: Project, rawPath: String?) {
  if (rawPath == null) return

  val path = rawPath.trim().ifEmpty { mcpFail("files must not contain blank paths") }
  val resolvedPath = resolveExistingRegularFileInProject(project = project, pathInProject = path)
  val file = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(resolvedPath)
             ?: mcpFail("File $resolvedPath doesn't exist or can't be opened")
  requestedFiles.putIfAbsent(file, path)
}


private class RequestedFormattingFile(
  @JvmField val path: String,
  @JvmField val virtualFile: VirtualFile,
  @JvmField val psiFile: PsiFile,
)