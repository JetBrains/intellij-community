@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndBackgroundWriteAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.transformer.TextPresentationTransformers
import com.intellij.util.DocumentUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlin.io.path.name
import kotlin.io.path.pathString

class PatchToolset : McpToolset {
  @McpTool
  @McpDescription("""
        Apply a patch using the Codex apply_patch format.
        Supports Add, Delete, and Update operations with optional Move to path for updates.
        Paths must stay inside the project directory.
    """)
  suspend fun apply_patch(
    @McpDescription("Patch text in the apply_patch format, including Begin/End markers.")
    input: String? = null,
    @McpDescription("Alias of `input` for compatibility with clients that send `{patch: ...}`.")
    patch: String? = null,
  ): String {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.applying.patch"))
    val project = currentCoroutineContext().project
    val fileDocumentManager = serviceAsync<FileDocumentManager>()
    val localFileSystem = LocalFileSystem.getInstance()

    val patchText = PatchApplyEngine.extractPatchText(input, patch)
    val operations = PatchApplyEngine.parsePatch(patchText)

    for (operation in operations) {
      when (operation) {
        is AddPatchOperation -> applyAdd(project, fileDocumentManager, operation)
        is DeletePatchOperation -> applyDelete(this, project, localFileSystem, operation)
        is UpdatePatchOperation -> applyUpdate(this, project, localFileSystem, fileDocumentManager, operation)
      }
    }

    val touched = operations.size
    val suffix = if (touched == 1) "" else "s"
    return "Applied patch to $touched file$suffix."
  }
}

private suspend fun applyAdd(project: Project, fileDocumentManager: FileDocumentManager, operation: AddPatchOperation) {
  val targetPath = project.resolveInProject(operation.path)
  val parentPath = targetPath.parent ?: mcpFail("Add File requires a parent directory")

  backgroundWriteAction {
    val parent = VfsUtil.createDirectories(parentPath.pathString)
    if (parent.findChild(targetPath.name) != null) mcpFail("File already exists: ${operation.path}")

    val createdFile = parent.findOrCreateFile(targetPath.name)
    writeFileTextByVfs(createdFile, operation.content, operation.path)
  }
}

private suspend fun applyDelete(
  requestor: Any,
  project: Project,
  localFileSystem: LocalFileSystem,
  operation: DeletePatchOperation,
) {
  val resolvedPath = project.resolveInProject(operation.path)
  val file = findFile(localFileSystem, resolvedPath, operation.path)
  if (file.isDirectory) mcpFail("Path is not a file: ${operation.path}")

  backgroundWriteAction {
    file.delete(requestor)
  }
}

private suspend fun applyUpdate(
  requestor: Any,
  project: Project,
  localFileSystem: LocalFileSystem,
  fileDocumentManager: FileDocumentManager,
  operation: UpdatePatchOperation,
) {
  val sourcePath = project.resolveInProject(operation.path)
  val sourceFile = findFile(localFileSystem, sourcePath, operation.path)
  if (sourceFile.isDirectory) mcpFail("Path is not a file: ${operation.path}")

  val hasCachedDocument = readAction {
    fileDocumentManager.getCachedDocument(sourceFile) != null
  }

  if (hasCachedDocument) {
    applyUpdateWithDocument(requestor, project, fileDocumentManager, sourceFile, sourcePath, operation)
  }
  else {
    applyUpdateWithoutDocument(requestor, project, sourceFile, sourcePath, operation)
  }
}

private suspend fun applyUpdateWithDocument(
  requestor: Any,
  project: Project,
  fileDocumentManager: FileDocumentManager,
  sourceFile: VirtualFile,
  sourcePath: java.nio.file.Path,
  operation: UpdatePatchOperation,
) {
  readAndEdtWriteAction {
    if (sourceFile.fileType.isBinary) mcpFail("File ${operation.path} is binary")
    val document = fileDocumentManager.getDocument(sourceFile)
                   ?: mcpFail("Could not get document for ${operation.path}")
    val originalText = TextPresentationTransformers.toPersistent(document.text, virtualFile = sourceFile).toString()
    val updatedText = PatchApplyEngine.applyHunks(originalText, operation.hunks)
    val moveTarget = operation.moveTo?.let { moveTo ->
      val resolved = project.resolveInProject(moveTo)
      if (resolved == sourcePath) null else moveTo to resolved
    }
    val hasContentChanges = updatedText != originalText
    if (!hasContentChanges && moveTarget == null) {
      return@readAndEdtWriteAction value(Unit)
    }

    writeAction {
      val targetFile: VirtualFile
      if (moveTarget != null) {
        val (moveTo, movePath) = moveTarget
        targetFile = moveFile(requestor, sourceFile, movePath, moveTo)
      }
      else {
        targetFile = sourceFile
      }

      if (hasContentChanges) {
        writeFileTextByDocument(fileDocumentManager, document, targetFile, updatedText)
      }
    }
  }
}

private suspend fun applyUpdateWithoutDocument(
  requestor: Any,
  project: Project,
  sourceFile: VirtualFile,
  sourcePath: java.nio.file.Path,
  operation: UpdatePatchOperation,
) {
  readAndBackgroundWriteAction {
    if (sourceFile.fileType.isBinary) mcpFail("File ${operation.path} is binary")
    val sourceText = VfsUtil.loadText(sourceFile)
    val originalText = TextPresentationTransformers.toPersistent(sourceText, virtualFile = sourceFile).toString()
    val updatedText = PatchApplyEngine.applyHunks(originalText, operation.hunks)
    val moveTarget = operation.moveTo?.let { moveTo ->
      val resolved = project.resolveInProject(moveTo)
      if (resolved == sourcePath) null else moveTo to resolved
    }
    val hasContentChanges = updatedText != originalText
    if (!hasContentChanges && moveTarget == null) {
      return@readAndBackgroundWriteAction value(Unit)
    }

    writeAction {
      val targetFile: VirtualFile
      val targetPathInProject: String
      if (moveTarget != null) {
        val (moveTo, movePath) = moveTarget
        targetFile = moveFile(requestor, sourceFile, movePath, moveTo)
        targetPathInProject = moveTo
      }
      else {
        targetFile = sourceFile
        targetPathInProject = operation.path
      }

      if (hasContentChanges) {
        writeFileTextByVfs(targetFile, updatedText, targetPathInProject)
      }
    }
  }
}

private fun findFile(localFileSystem: LocalFileSystem, resolvedPath: java.nio.file.Path, pathInProject: String): VirtualFile {
  return localFileSystem.findFileByNioFile(resolvedPath)
         ?: localFileSystem.refreshAndFindFileByNioFile(resolvedPath)
         ?: mcpFail("File not found: $pathInProject")
}

private fun moveFile(requestor: Any, file: VirtualFile, targetPath: java.nio.file.Path, targetPathInProject: String): VirtualFile {
  val parentPath = targetPath.parent ?: mcpFail("Move to requires a parent directory")
  val targetParent = VfsUtil.createDirectories(parentPath.pathString)
  val targetName = targetPath.name
  val existing = targetParent.findChild(targetName)
  if (existing != null && existing != file) mcpFail("File already exists: $targetPathInProject")

  if (file.parent != targetParent) {
    file.move(requestor, targetParent)
  }
  if (file.name != targetName) {
    file.rename(requestor, targetName)
  }
  return file
}

private fun writeFileTextByDocument(
  fileDocumentManager: FileDocumentManager,
  document: Document,
  file: VirtualFile,
  text: String,
) {
  val documentText = TextPresentationTransformers.fromPersistent(text, virtualFile = file)
  DocumentUtil.executeInBulk(document, true) {
    document.setText(documentText)
  }
  fileDocumentManager.saveDocument(document)
}

private fun writeFileTextByVfs(
  file: VirtualFile,
  text: String,
  pathInProject: String,
) {
  val documentText = TextPresentationTransformers.fromPersistent(text, virtualFile = file)
  try {
    VfsUtil.saveText(file, documentText.toString())
  }
  catch (e: Exception) {
    mcpFail("Could not write file $pathInProject: ${e.message}")
  }
}
