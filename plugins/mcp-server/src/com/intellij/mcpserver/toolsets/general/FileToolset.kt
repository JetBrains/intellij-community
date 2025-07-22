@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.*
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.mcpserver.util.renderDirectoryTree
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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
        |Searches for all files in the project whose names contain the specified keyword (case-insensitive).
        |Use this tool to locate files when you know part of the filename.
        |Note: Matched only names, not paths, because works via indexes.
        |Note: Only searches through files within the project directory, excluding libraries and external dependencies.
        |Note: Prefer this tool over other `find` tools because it's much faster, 
        |but remember that this tool searches only names, not paths and it doesn't support glob patterns.
    """)
  suspend fun find_files_by_name_keyword(
    @McpDescription("Substring to search for in file names")
    nameKeyword: String,
    @McpDescription("Maximum number of files to return.")
    fileCountLimit: Int = 1000,
    @McpDescription("Timeout in milliseconds")
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
  ): FilesListResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.finding.files.by.name", nameKeyword))
    val project = currentCoroutineContext().project
    val projectDir = project.projectDirectory

    val globalSearchScope = GlobalSearchScope.projectScope(project)
    val result = CopyOnWriteArrayList<Path>()

    val timedOut = withTimeoutOrNull(timeout.milliseconds) {
      withBackgroundProgress(project, McpServerBundle.message("progress.title.searching.for.files.by.name", nameKeyword), cancellable = true) {
        readAction {
          val fileSequence = FilenameIndex.getAllFilenames(project)
            .asSequence()
            .filter { it.contains(nameKeyword, ignoreCase = true) }
            .flatMap {
              FilenameIndex.getVirtualFilesByName(it, globalSearchScope)
            }
            .mapNotNull { file ->
              runCatching { projectDir.relativize(file.toNioPath()) }.getOrNull()
            }
            .take(fileCountLimit)
          for (file in fileSequence) {
            ensureActive()
            result.add(file)
          }
        }
      }
    } == null
    return FilesListResult(probablyHasMoreMatchingFiles = result.size >= fileCountLimit,
                           timedOut = timedOut,
                           files = result.map { it.pathString })
  }

  @OptIn(ExperimentalAtomicApi::class)
  @McpTool
  @McpDescription("""
          |Searches for all files in the project whose relative paths match the specified glob pattern.
          |The search is performed recursively in all subdirectories of the project directory or a specified subdirectory.
          |Use this tool when you need to find files by a glob pattern (e.g. '**/*.txt').
    """)
  suspend fun find_files_by_glob(
    @McpDescription("Glob pattern to search for. The pattern must be relative to the project root. Example: `src/**/ *.java`")
    globPattern: String,
    @McpDescription("Optional subdirectory relative to the project to search in.")
    subDirectoryRelativePath: String? = null,
    @McpDescription("Whether to add excluded/ignored files to the search results. Files can be excluded from a project either by user of by some ignore rules")
    addExcluded: Boolean = false,
    @McpDescription("Maximum number of files to return.")
    fileCountLimit: Int = 1000,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE
  ) : FilesListResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.finding.files.by.glob", globPattern))
    val project = currentCoroutineContext().project
    val projectDirPath = project.projectDirectory
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()

    val globMather = FileSystems.getDefault().getPathMatcher("glob:$globPattern") ?: mcpFail("Invalid glob pattern: $globPattern")
    val result = CopyOnWriteArrayList<Path>()

    val contentIterator = ContentIterator { file ->
      if (file.isDirectory) return@ContentIterator true
      val filePath = file.toNioPathOrNull() ?: return@ContentIterator true // continue iteration
      val relativePath = runCatching { projectDirPath.relativize(filePath) }.getOrNull() ?: return@ContentIterator true

      if (!globMather.matches(relativePath)) return@ContentIterator true
      if (!addExcluded && runReadAction { fileIndex.isExcluded(file) }) return@ContentIterator true
      result.add(relativePath)

      return@ContentIterator result.size < fileCountLimit
    }

    val timedOut = withTimeoutOrNull(timeout.milliseconds) {
      withBackgroundProgress(project, McpServerBundle.message("progress.title.searching.for.files.by.glob.pattern", globPattern), cancellable = true) {
        if (subDirectoryRelativePath != null) {
          val subDirectoryPath = project.resolveInProject(subDirectoryRelativePath)
          if (!subDirectoryPath.exists() && !subDirectoryPath.isDirectory()) mcpFail("Subdirectory not found or not a directory: $subDirectoryPath")
          val subdirectoryVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(subDirectoryPath)
                                        ?: mcpFail("Subdirectory not found: $subDirectoryPath")
          fileIndex.iterateContentUnderDirectory(subdirectoryVirtualFile, contentIterator)
        }
        else {
          fileIndex.iterateContent(contentIterator)
        }
      }
    } == null

    return FilesListResult(probablyHasMoreMatchingFiles = result.size >= fileCountLimit, // there may be a very rare case when the count of files is exactly the limit, but it's not a problem
                           timedOut = timedOut,
                           files = result.map { it.pathString })
  }

  @Serializable
  data class FilesListResult(
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val probablyHasMoreMatchingFiles: Boolean = false,
    @property:McpDescription(Constants.TIMED_OUT_DESCRIPTION)
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean? = false,
    val files: List<String>
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
  ) {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.creating.file", pathInProject))
    val project = currentCoroutineContext().project

    val path = project.resolveInProject(pathInProject)
    val newFile = try {
      LocalFileSystem.getInstance().createChildFile(null, VfsUtil.createDirectories(path.parent.pathString), path.name)
    }
    catch (io: IOException) {
      mcpFail("Can't create file: $path: ${io.message}")
    }
    // newFile point to a fake file, so we need to refresh it to get a real one
    val createdFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: mcpFail("File $path wasn't created")
    writeAction {
      val document = FileDocumentManager.getInstance().getDocument(createdFile) ?: mcpFail("Can't get document for created file: $newFile")
      if (text != null) {
        document.setText(text)
      }
    }
  }
}