@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.mcpserver.util.renderDirectoryTree
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.openapi.vfs.transformer.TextPresentationTransformers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

class FileToolset : McpToolset {
  @McpTool
  @McpDescription("""
        |Provides a tree representation of the specified directory in the pseudo graphic format like `tree` utility does.
        |Use this tool to explore the contents of a directory or the whole project.
        |You MUST prefer this tool over listing directories via command line utilities like `ls` or `dir`.
    """)
  suspend fun list_directory_tree(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION) directoryPath: String,
    @McpDescription("Maximum recursion depth") maxDepth: Int = 5,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION) timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
  ): DirectoryTreeInfo {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.traversing.folder.tree", directoryPath))
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(directoryPath)
    if (!resolvedPath.exists()) mcpFail("No such directory: $resolvedPath")
    if (!resolvedPath.isDirectory()) mcpFail("Not a directory: $resolvedPath")

    val result = StringBuilder()
    val errors = mutableListOf<String>()
    val timedOut = withTimeoutOrNull(timeout.milliseconds) { renderDirectoryTree(resolvedPath.toFile(), result, errors, maxDepth = maxDepth) } == null
    return DirectoryTreeInfo(directoryPath, result.toString(), errors, timedOut)
  }

  @Serializable
  class DirectoryTreeInfo(
    val traversedDirectory: String,
    val tree: String,
    val errors: List<String>,
    @property:McpDescription(Constants.TIMED_OUT_DESCRIPTION)
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val listingTimedOut: Boolean? = false,
  )

  @McpTool
  @McpDescription("""
        |Opens the specified file in the JetBrains IDE editor.
        |Requires a filePath parameter containing the path to the file to open.
        |The file path can be absolute or relative to the project root.
    """)
  suspend fun open_file_in_editor(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    filePath: String,
  ) {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.opening.file", filePath))
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(filePath)

    val file = LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
               ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)

    if (file == null || !file.exists() || !file.isFile) mcpFail("File $filePath doesn't exist or can't be opened")

    withContext(Dispatchers.EDT) {
      FileEditorManagerEx.getInstanceExAsync(project).openFile(file, options = FileEditorOpenOptions(requestFocus = true))
    }
  }

  @McpTool
  @McpDescription("""
        |Returns active editor's and other open editors' file paths relative to the project root.
        |
        |Use this tool to explore current open editors.
    """)
  suspend fun get_all_open_file_paths(): OpenFilesInfo {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.getting.open.files"))
    val project = currentCoroutineContext().project
    val projectDir = project.projectDirectory

    val fileEditorManager = FileEditorManagerEx.getInstanceExAsync(project)
    val openFiles = fileEditorManager.openFiles
    val filePaths = openFiles.mapNotNull { projectDir.relativizeIfPossible(it) }
    val activeFilePath = fileEditorManager.selectedEditor?.file?.toNioPathOrNull()?.let { projectDir.relativize(it).pathString }
    return OpenFilesInfo(activeFilePath = activeFilePath, openFiles = filePaths)
  }

  @Serializable
  data class OpenFilesInfo(
    val activeFilePath: String?,
    val openFiles: List<String>
  )

  @McpTool
  @McpDescription("""
        |Creates a new file at the specified path within the project directory and optionally populates it with text if provided.
        |Use this tool to generate new files in your project structure.
        |Note: Creates any necessary parent directories automatically
    """)
  suspend fun create_new_file(
    @McpDescription("Path where the file should be created relative to the project root")
    pathInProject: String,
    @McpDescription("Content to write into the new file")
    text: String? = null,
    @McpDescription("Whether to overwrite an existing file if exists. If false, an exception is thrown in case of a conflict.")
    overwrite: Boolean = false,
  ) {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.creating.file", pathInProject))
    val project = currentCoroutineContext().project

    val path = project.resolveInProject(pathInProject)
    try {
      writeAction {
        val parent = VfsUtil.createDirectories(path.parent.pathString)
        val existing = parent.findChild(path.name)
        if (existing != null && !overwrite) mcpFail("File already exists: $pathInProject. Specify 'overwrite=true' to overwrite it")
        val createdFile = parent.findOrCreateFile(path.name)

        if (text != null) {
          val documentText = TextPresentationTransformers.fromPersistent(text, virtualFile = createdFile)
          val document = FileDocumentManager.getInstance().getDocument(createdFile) ?: mcpFail("Can't get document for created file: $pathInProject")
          document.setText(documentText)
        }
      }
    }
    catch (io: IOException) {
      mcpFail("Can't create file: $path: ${io.message}")
    }
  }
}