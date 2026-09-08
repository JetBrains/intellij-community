// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.pyProjectToml

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
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
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
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
 * Fact 1 can be turned off with the system property `indexing.filename.over.vfs`. `FilenameIndex` is then a
 * content index, and it holds no file outside a content root. A directory that waits to become a module lies
 * outside every content root, so the query would report too few files and the model would lose a module.
 * This method therefore fails instead. No host of this code turns the property off today.
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

  check(FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX) {
    "The pyproject.toml model needs the filename index over the VFS. " +
    "The system property indexing.filename.over.vfs turns it off, and the model would then miss a module."
  }

  val files = queryFilenameIndex()
  if (files.isEmpty()) return emptyList()

  return withContext(Dispatchers.IO) {
    val filter = PyProjectTomlPathFilter(roots, excludedPaths)
    files.asSequence()
      // The index holds a directory too, and the name enumerator is application wide.
      // A directory named `pyproject.toml`, or a file of another open project, must not become a module.
      .filter { !it.isDirectory }
      .filter { filter.accepts(it) }
      .mapNotNull { it.toNioPathOrNull() }
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
 * Only the last rule needs a syscall.
 */
private class PyProjectTomlPathFilter(
  roots: Set<Directory>,
  private val excludedPaths: Set<Path>,
) {
  private val virtualEnvReader = VirtualEnvReader()
  private val cache = HashMap<VirtualFile, Boolean>()

  /**
   * The roots as the VFS holds them.
   *
   * A root that the VFS does not hold is dropped, and it loses no file. The filename index reports what the
   * VFS knows, so such a root has no hit to accept.
   */
  private val rootFiles: List<VirtualFile> =
    roots.mapNotNull { LocalFileSystem.getInstance().findFileByNioFile(it) }

  /**
   * The walk compares a [VirtualFile] and never a name.
   *
   * The VFS resolves a name as the filesystem of that directory does, and two spellings of one directory
   * reach the same [VirtualFile]. A comparison of paths would need the case rule of that directory, and
   * `Path` reads no such rule while `SystemInfoRt.isFileSystemCaseSensitive` is one guess for the whole
   * system. See [VirtualFile.isCaseSensitive] (PY-91841).
   */
  fun accepts(tomlFile: VirtualFile): Boolean {
    val root = rootFiles.firstOrNull { VfsUtilCore.isAncestor(it, tomlFile, true) } ?: return false
    var directory: VirtualFile? = tomlFile.parent
    while (directory != null) {
      if (!isVisible(directory)) return false
      if (directory == root) return true
      directory = directory.parent
    }
    // `isAncestor` found the root, so the walk up from the file reaches it.
    return false
  }

  // These rules decide the model. `loadSubtreesIntoVfs` prunes the VFS walk with the same rules, so a rule
  // added here must reach that walk too, or the walk loads a subtree that the model then rejects. The name
  // rule is shared through [isPrunedName]. The excluded path and the interpreter stay in both places.
  private fun isVisible(directory: VirtualFile): Boolean = cache.getOrPut(directory) {
    val path = directory.toNioPathOrNull()
    // The order of the checks follows their cost. A check of the name needs no syscall (PY-91826).
    when {
      directory.name.isPrunedName() -> false
      path == null -> false
      path in excludedPaths -> false
      // A link that points into a build cache can hold a second copy of the whole project, and every copy
      // would become a module. A build directory that links to the execroot of the build tool is the common
      // case. The VFS records the property when it reads the directory, so this rule needs no syscall.
      directory.`is`(VFileProperty.SYMLINK) -> false
      else -> virtualEnvReader.findPythonInPythonRoot(path) == null
    }
  }
}

/**
 * True when a directory of this name never holds a `pyproject.toml` that becomes a module.
 *
 * The search and the subtree load read one rule, so neither can prune a name that the other keeps. A load
 * that prunes more hides a `pyproject.toml` from the search, and the model loses that module.
 *
 * A name that starts with a dot is pruned, which already covers every dot name of `PRUNED_SCAN_DIRS`. The
 * set [PRUNED_SCAN_DIRS_NO_DOT] holds the rest, so the two tests together cover the whole list.
 */
internal fun String.isPrunedName(): Boolean = startsWith(".") || this in PRUNED_SCAN_DIRS_NO_DOT

private val log = fileLogger()
