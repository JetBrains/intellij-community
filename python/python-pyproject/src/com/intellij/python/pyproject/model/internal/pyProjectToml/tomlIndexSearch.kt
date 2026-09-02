// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.pyProjectToml

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.IdFilter
import com.jetbrains.python.venvReader.Directory
import com.jetbrains.python.venvReader.PRUNED_SCAN_DIRS_NO_DOT
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Finds every [PY_PROJECT_TOML] under [roots] with the VFS filename index.
 *
 * Three platform facts make this correct, and all three are easy to lose on a refactoring:
 *
 * 1. [FilenameIndex] is not a content index. `FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX` is `true`
 *    by default, so `FileBasedIndexScanUtil` answers the query from the VFS name enumerator
 *    (`FSRecords.processFilesWithNames`). The lookup needs no indexing pass.
 * 2. The VFS knows a directory only after something loaded it. The scanning pass and the initial VFS refresh
 *    reach the content roots only, so the caller must load the project roots and every new directory itself.
 *    `PyProjectModelSyncService` does both.
 * 3. The query needs [ALL_FILES_SCOPE] and [ACCEPT_EVERY_FILE]. See their own comments.
 *
 * Fact 1 can be turned off, and [findPyProjectTomlByVfsWalk] then replaces the query.
 *
 * A file survives only when every directory between it and its root passes five rules: no dot directory,
 * no name in [PRUNED_SCAN_DIRS_NO_DOT], no directory in [excludedPaths], no symbolic link, and no virtualenv.
 */
@ApiStatus.Internal
suspend fun findPyProjectTomlFilesInIndex(
  roots: Set<Directory>,
  excludedPaths: Set<Path>,
): List<Path> {
  if (roots.isEmpty()) return emptyList()

  val files =
    if (FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX) queryFilenameIndex()
    else findPyProjectTomlByVfsWalk(roots, excludedPaths)
  if (files.isEmpty()) return emptyList()

  return withContext(Dispatchers.IO) {
    val filter = PyProjectTomlPathFilter(roots, excludedPaths)
    files.asSequence()
      // The index holds a directory too, and the name enumerator is application wide.
      // A directory named `pyproject.toml`, or a file of another open project, must not become a module.
      .filter { !it.isDirectory }
      .mapNotNull { it.toNioPathOrNull() }
      .filter { filter.accepts(it) }
      .toList()
  }
}

/** Asks the filename index for every [PY_PROJECT_TOML] the VFS holds. */
private suspend fun queryFilenameIndex(): List<VirtualFile> = readAction {
  // The list must be built inside the read action. A suspending `readAction` is cancelled and retried when a
  // write action starts, and a list outside the block would then hold every hit twice.
  val hits = ArrayList<VirtualFile>()
  // The filename index is served by the VFS, so RELIABLE_DATA_ONLY is enough and the call never waits
  // for smart mode. `VgoStatusTracker` reads `go.mod` the same way.
  DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(Runnable {
    FilenameIndex.processFilesByNames(setOf(PY_PROJECT_TOML), true, ALL_FILES_SCOPE, ACCEPT_EVERY_FILE) { file ->
      // `FileBasedIndexScanUtil` reports whatever `findFileById` returns, and it checks no validity.
      // `FilenameIndex.processFilesByName` guards the same way.
      if (file.isValid) {
        hits.add(file)
      }
      true
    }
  })
  hits
}

/**
 * Walks the VFS from [roots], for the case that the filename index is not served by the VFS.
 *
 * The system property `indexing.filename.over.vfs` can turn `FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX`
 * off. `FilenameIndex` is then a content index, and it holds no file outside a content root. A directory that
 * waits to become a module lies outside every content root, so the query would report too few files and the
 * model would lose a module with no error. This walk reads the same VFS instead, hence it needs no index.
 *
 * The walk touches no disk. It prunes the same names as [PyProjectTomlPathFilter], which stays the authority.
 */
private suspend fun findPyProjectTomlByVfsWalk(roots: Set<Directory>, excludedPaths: Set<Path>): List<VirtualFile> =
  withContext(Dispatchers.IO) {
    log.warn("The filename index is not served by the VFS. The pyproject.toml search walks the VFS instead.")
    val localFileSystem = LocalFileSystem.getInstance()
    val virtualEnvReader = VirtualEnvReader()
    val hits = ArrayList<VirtualFile>()
    for (root in roots) {
      coroutineContext.ensureActive()
      val rootDirectory = localFileSystem.findFileByNioFile(root) ?: continue
      VfsUtilCore.visitChildrenRecursively(rootDirectory, object : VirtualFileVisitor<Unit>(NO_FOLLOW_SYMLINKS) {
        override fun visitFile(file: VirtualFile): Boolean {
          if (!file.isDirectory) {
            if (file.name == PY_PROJECT_TOML) hits.add(file)
            return true
          }
          // The name of a root is never checked, because a project may itself live under a dot directory.
          if (file == rootDirectory) return true
          if (file.name.startsWith(".") || file.name in PRUNED_SCAN_DIRS_NO_DOT) return false
          val path = file.toNioPathOrNull() ?: return true
          return path !in excludedPaths && virtualEnvReader.findPythonInPythonRoot(path) == null
        }
      })
    }
    hits
  }

/**
 * The query must reach every name the VFS holds. Two platform filters stand in the way, and both hide a file
 * that must still become a module.
 *
 * `GlobalSearchScope.everythingScope` does not mean every file. `ProjectScopeBuilderImpl.buildEverythingScope`
 * accepts a file only when it is an indexable file of the project, or when `WorkspaceFileIndex.isInWorkspace`
 * accepts it. Neither holds for a `pyproject.toml` that lies under the project base path but outside a content
 * root, which is the state of every directory before the model creates its module.
 */
private val ALL_FILES_SCOPE: GlobalSearchScope = object : GlobalSearchScope() {
  override fun contains(file: VirtualFile): Boolean = true
  override fun isSearchInModuleContent(aModule: Module): Boolean = true
  override fun isSearchInLibraries(): Boolean = true
}

/**
 * The second filter. Without an explicit [IdFilter] the platform narrows the query to the indexable files of
 * the project, and that set is empty until the first scanning ends.
 *
 * [PyProjectTomlPathFilter] does the filtering instead.
 */
private val ACCEPT_EVERY_FILE: IdFilter = object : IdFilter() {
  override fun containsFileId(id: Int): Boolean = true
}

/**
 * Applies the directory rules to one file at a time.
 *
 * A flat list of hits shares its directories, so this class caches the answer per directory.
 * Only the last two rules need a syscall.
 */
private class PyProjectTomlPathFilter(
  private val roots: Set<Directory>,
  private val excludedPaths: Set<Path>,
) {
  private val virtualEnvReader = VirtualEnvReader()
  private val cache = HashMap<Path, Boolean>()

  fun accepts(tomlFile: Path): Boolean {
    val root = roots.firstOrNull { isUnder(it, tomlFile) } ?: return false
    var directory: Path? = tomlFile.parent
    while (directory != null) {
      if (!isVisible(directory)) return false
      if (FileUtil.pathsEqual(directory.toString(), root.toString())) return true
      directory = directory.parent
    }
    // The loop leaves the root only when `startsWith` and `parent` disagree, which must not happen.
    return false
  }

  private fun isVisible(directory: Path): Boolean = cache.getOrPut(directory) {
    val name = directory.name
    // The order of the checks follows their cost. A check of the name needs no syscall (PY-91826).
    when {
      name.startsWith(".") || name in PRUNED_SCAN_DIRS_NO_DOT || directory in excludedPaths -> false
      // `Files.walkFileTree` never follows a link, and the VFS always does. A link that points into a build
      // cache can hold a second copy of the whole project, and every copy would become a module. A build
      // directory that links to the execroot of the build tool is the common case.
      Files.isSymbolicLink(directory) -> false
      else -> virtualEnvReader.findPythonInPythonRoot(directory) == null
    }
  }
}

/**
 * True when [file] lies under [root].
 *
 * [Path.startsWith] is not enough on a case-insensitive filesystem of macOS or of Linux. `UnixPath` compares
 * the bytes of a name, and it reads no property of the filesystem. A root of another case then rejects every
 * file, and the model loses every module without an error.
 *
 * The default volume of macOS is case-insensitive. `Files.isDirectory` accepts the other case of a name
 * there, and `Path.startsWith` rejects it.
 *
 * [FileUtil.isAncestor] reads `SystemInfoRt.isFileSystemCaseSensitive`, so it follows the filesystem. The
 * two arguments become strings for that reason alone.
 *
 * `WindowsPath` needs none of this. It compares two names without the case already.
 */
private fun isUnder(root: Path, file: Path): Boolean = FileUtil.isAncestor(root.toString(), file.toString(), true)

private val log = fileLogger()
