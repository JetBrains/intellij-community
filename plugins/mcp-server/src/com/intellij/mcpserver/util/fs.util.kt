package com.intellij.mcpserver.util

import com.intellij.mcpserver.mcpFail
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.project.ProjectStoreOwner
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path

private val logger = fileLogger()
/**
 * Returns the project's base directory as a [Path].
 *
 * If the project directory cannot be determined, an McpExpectedException is thrown.
 *
 * Consider using [com.intellij.openapi.project.BaseProjectDirectories] instead, it handles more cases.
 */
val Project.projectDirectory: Path
  get() {
    // don't use guessProjectDir() here because it may point to some internal directory (e.g. src instead of project root)
    return if (this is ProjectStoreOwner) componentStore.projectBasePath
    else mcpFail("The project directory cannot be determined.")
  }

/**
 * Resolves a relative path against the project's directory.
 *
 * When [throwWhenOutside] is true the method throws an McpExpectedException if the path is outside the project directory.
 */
fun Project.resolveInProject(pathInProject: String, throwWhenOutside: Boolean = true): Path {
  val filePath = projectDirectory.resolve(pathInProject).normalize()
  if (throwWhenOutside && !isInProjectDirectories(filePath)) {
    mcpFail("Specified path '$filePath' points to the location outside of the project directory")
  }
  return filePath
}

/**
 * Whether [path] is inside one of the project's base directories.
 */
fun Project.isInProjectDirectories(path: Path): Boolean {
  logger.assertTrue(path.isAbsolute, "Expected an absolute path, got '$path'")
  return projectDirectories().any { path.startsWith(it) }
}

/**
 * The directories a file may live in and still belong to the project: the project directory itself plus every root
 * [BaseProjectDirectories] reports.
 */
fun Project.projectDirectories(): List<Path> =
  (listOf(projectDirectory.normalize()) + baseProjectDirectories()).distinct()

/**
 * The roots [BaseProjectDirectories] reports for this project, or an empty list when they cannot be obtained.
 */
private fun Project.baseProjectDirectories(): List<Path> =
  readSafely("the base directories", emptyList()) {
    getBaseDirectories().mapNotNull { it.toNioPathOrNull()?.normalize() }
  }

/**
 * Reads a part of the project state, and returns [fallback] when the project cannot supply it. A project that is
 * disposed in parallel throws from every service it owns, and one failed project must not break the whole lookup.
 */
private fun <T> Project.readSafely(what: String, fallback: T, action: () -> T): T =
  try {
    action()
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (error: Throwable) {
    logger.warn("Failed to read $what of the project '$name'", error)
    fallback
  }

/**
 * Relativizes [path] against this directory, falling back to the absolute path when the two share no root.
 */
fun Path.relativizeIfPossible(path: Path): String =
  try {
    relativize(path).toString()
  }
  catch (_: IllegalArgumentException) {
    path.toString()
  }

fun Project.getPathForMcp(): String? {
  return basePath
}

suspend fun findMostRelevantProjectForRoots(roots: Collection<String>): Project? {
  return roots.firstNotNullOfOrNull { findMostRelevantProject (it) }
}

suspend fun findMostRelevantProject(path: String): Project? {
  val parsedPath = parsePathForProjectLookup(path) ?: return null
  return findMostRelevantProject(parsedPath.normalize())
}

internal fun parsePathForProjectLookup(path: String): Path? {
  val systemIndependentPath = FileUtilRt.toSystemIndependentName(path).trim()
  if (systemIndependentPath.isEmpty()) return null

  return try {
    if (systemIndependentPath.startsWith("file://")) {
      Path.of(UrlClassLoader.urlToFilePath(systemIndependentPath))
    }
    else {
      Path.of(systemIndependentPath)
    }
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (error: Throwable) {
    logger.trace { "Failed to parse project path '$path': ${error.message}" }
    null
  }
}

private suspend fun findMostRelevantProject(path: Path): Project? {
  if (!path.isAbsolute) {
    logger.trace { "Path is not absolute: $path" }
    return null
  }
  val targetNormalizedPath = path.normalize()
  val openProjects = serviceAsync<ProjectManager>().openProjects

  // a project is matched not only by its own directory, but by every directory `BaseProjectDirectories` reports for it:
  // a solution opened from a repository subdirectory keeps the repository root attached, and that root is a valid
  // location of the project even though the project directory is below it
  //
  // prefer most inner directories
  // let's say we have
  // - frontend (a project)
  // - frontend/common (also a separate project but in the inner dir)
  // - frontend/common/src  <-- this path passed as `path`
  // here we will have 2 project matches: `frontend/common` and `frontend` and better to prefer `frontend/common`
  val matches = openProjects.flatMap { it.matchingDirectories(targetNormalizedPath) }.sortedWith(MOST_INNER_MATCH_FIRST)
  logger.trace { "Found projects for path $path: ${matches.joinToString { "${it.project.getPathForMcp()} (matched by ${it.directory})" }}" }
  val bestMatch = matches.firstOrNull() ?: return null

  // two projects opened from the same repository report the same repository root, and the root alone does not tell one
  // from the other: report no project, so that the caller asks for an explicit project path
  if (!bestMatch.isProjectDirectory &&
      matches.any { it.project !== bestMatch.project && it.directory == bestMatch.directory }) {
    logger.trace { "The path $path matches the directory ${bestMatch.directory} of more than one project" }
    return null
  }
  return bestMatch.project
}

/**
 * A directory of [project] that contains the looked-up path. [isProjectDirectory] tells the project's own directory from
 * a base directory of the project.
 */
private class ProjectDirectoryMatch(val project: Project, val directory: Path, val isProjectDirectory: Boolean)

/**
 * Orders matches from the most to the least specific: a deeper directory wins, and the project's own directory wins over
 * a base directory pointing at the very same place.
 */
private val MOST_INNER_MATCH_FIRST: Comparator<ProjectDirectoryMatch> =
  compareByDescending<ProjectDirectoryMatch> { it.directory.nameCount }.thenByDescending { it.isProjectDirectory }

/**
 * The directories of this project that contain [targetNormalizedPath].
 */
private fun Project.matchingDirectories(targetNormalizedPath: Path): List<ProjectDirectoryMatch> {
  if (isDisposed) return emptyList()

  return readSafely("the directories", emptyList()) {
    val projectPath = if (this is ProjectStoreOwner) componentStore.projectBasePath.normalize()
                      else getPathForMcp()?.toNioPathOrNull()?.normalize()

    val directories = LinkedHashSet<Path>()
    projectPath?.let(directories::add)
    directories.addAll(baseProjectDirectories())

    directories.mapNotNull { directory ->
      if (!targetNormalizedPath.startsWith(directory)) return@mapNotNull null
      ProjectDirectoryMatch(project = this, directory = directory, isProjectDirectory = directory == projectPath)
    }
  }
}

/**
 * Tries to relativize [virtualFile]'s path relatively to [Path].
 */
fun Path.relativizeIfPossible(virtualFile: VirtualFile): String {
  val nioPath = virtualFile.toNioPathOrNull()
                ?: try {
                  Path.of(virtualFile.path)
                }
                catch (_: Throwable) {
                  null
                }
  if (nioPath == null) return virtualFile.path
  return try {
    relativize(nioPath).toString()
  }
  catch (_: IllegalArgumentException) {
    virtualFile.path
  }
}

// TODO: this must be unified with resolveInProject and made more flexible to support multiple source roots, also MCP client roots and so on
@RequiresBackgroundThread
fun resolveReadFile(project: Project, filePath: String): VirtualFile {
  val normalizedPath = normalizeReadFilePath(filePath)
  val virtualFileManager = VirtualFileManager.getInstance()
  val file = when {
    looksLikeVfsUrl(normalizedPath) -> {
      virtualFileManager.refreshAndFindFileByUrl(normalizedPath)
    }
    normalizedPath.contains(JarFileSystem.JAR_SEPARATOR) -> {
      val resolvedPath = resolveReadFilePath(project, normalizedPath)
      resolveArchiveEntryFile(FileUtilRt.toSystemIndependentName(resolvedPath))
    }
    else -> {
      val resolvedPath = resolveReadFilePath(project, normalizedPath)
      LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtilRt.toSystemIndependentName(resolvedPath))
    }
  } ?: mcpFail("File $filePath doesn't exist or can't be opened")

  if (file.isDirectory) {
    mcpFail("File $filePath is a directory")
  }
  return file
}

private fun normalizeReadFilePath(filePath: String): String {
  val normalizedPath = FileUtilRt.toSystemIndependentName(filePath).trim()
  if (normalizedPath.isEmpty()) {
    mcpFail("file_path is empty")
  }
  return normalizedPath
}

private fun resolveReadFilePath(project: Project, filePath: String): String {
  val path = try {
    Path.of(filePath)
  }
  catch (_: InvalidPathException) {
    mcpFail("Invalid path: $filePath")
  }
  return if (path.isAbsolute) {
    path.normalize().toString()
  }
  else {
    project.projectDirectory.resolve(path).normalize().toString()
  }
}

private fun resolveArchiveEntryFile(archiveEntryPath: String): VirtualFile? {
  val separatorIndex = archiveEntryPath.indexOf(JarFileSystem.JAR_SEPARATOR)
  if (separatorIndex <= 0) return null

  val localRootPath = archiveEntryPath.substring(0, separatorIndex)
  val entryPath = archiveEntryPath.substring(separatorIndex + JarFileSystem.JAR_SEPARATOR.length).trimStart('/')
  val localRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(localRootPath) ?: return null
  val virtualFileManager = VirtualFileManager.getInstance()

  val archiveRoot = listOf(StandardFileSystems.JAR_PROTOCOL, StandardFileSystems.JRT_PROTOCOL)
    .firstNotNullOfOrNull { protocol ->
      (virtualFileManager.getFileSystem(protocol) as? ArchiveFileSystem)?.getRootByLocal(localRoot)
    } ?: return null

  return if (entryPath.isEmpty()) archiveRoot else archiveRoot.findFileByRelativePath(entryPath)
}

fun looksLikeVfsUrl(filePath: String): Boolean {
  val schemeSeparator = filePath.indexOf("://")
  return schemeSeparator > 0 && filePath.substring(0, schemeSeparator).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
}

enum class RenderStyle {
  NameOnly,
  AbsolutePath,
}

suspend fun renderDirectoryTree(
  dir: File,
  result: StringBuilder,
  errorsBag: MutableList<String>,
  indent: String = "",
  isLast: Boolean = true,
  maxDepth: Int = 10,
  renderStyle: RenderStyle = RenderStyle.AbsolutePath,
) {
  if (maxDepth <= 0) return
  currentCoroutineContext().ensureActive()
  try {
    val prefix = when {
      indent.isEmpty() -> ""
      isLast -> "└── "
      else -> "├── "
    }
    result
      .append(indent)
      .append(prefix)
      .append(if (renderStyle == RenderStyle.AbsolutePath) dir.absolutePath else dir.name)
      .append(if (dir.isDirectory) "/" else "")
      .append("\n")

    if (dir.isDirectory) {
      val children = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
      for ((index, file) in children.withIndex()) {
        val isLastChild = index == children.lastIndex
        val newIndent = indent + if (isLast) "    " else "│   "
        renderDirectoryTree(file, result, errorsBag, newIndent, isLastChild, maxDepth - 1, renderStyle = RenderStyle.NameOnly)
      }
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    errorsBag.add("Failed to read ${dir.absolutePath}: ${e.message ?: "unknown error"}")
  }
}
