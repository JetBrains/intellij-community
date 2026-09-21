// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.pyProjectToml.isPrunedName
import com.intellij.util.containers.CollectionFactory
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val log = fileLogger()

/**
 * Calls [onChange] when a VFS change can add, remove or alter a `pyproject.toml` under [knownRoots].
 *
 * The listener must stay cheap, because the platform calls it for every batch of VFS events.
 * It therefore reads only the name, the kind and the path of each event.
 *
 * [knownRoots] returns the roots of the model, as [PyProjectModelSyncService] last computed them.
 * The listener is application wide, so this set is the only way to tell a change of this project from a change
 * of another project, or of a directory that no project holds.
 */
internal fun subscribeToPyProjectTomlChanges(
  parentDisposable: Disposable,
  knownRoots: () -> Set<Path>,
  onChange: (RebuildRequest) -> Unit,
) {
  // The backgroundable variant keeps the applier off the EDT. The applier only hands the work over, and the
  // platform recommends this registration for every new listener.
  VirtualFileManager.getInstance().addAsyncFileListenerBackgroundable(AsyncFileListener { events ->
    // The root set is read once per batch, because a rebuild can replace it while the batch is processed.
    val eventFilter = EventFilter(knownRoots())
    var tomlChanged = false
    // A create event has no file yet, so the directory is resolved later, in `afterVfsChange`.
    val newDirectoryEvents = ArrayList<VFileEvent>()
    // The reason of the rebuild. Only the first kept event is described, because the description reads the
    // path and the listener must stay cheap.
    var keptEvents = 0
    var firstKeptEvent: String? = null
    for (event in events) {
      // A Git checkout produces tens of thousands of events, and `prepareChange` must stay cancellable.
      ProgressManager.checkCanceled()
      if (eventFilter.isIgnored(event)) continue
      val kept = when {
        event.isPyProjectToml() -> true.also { tomlChanged = true }
        event.requiresSubtreeLoad() -> true.also { newDirectoryEvents.add(event) }
        event.removesDirectory() -> true.also { tomlChanged = true }
        else -> false
      }
      if (kept) {
        keptEvents++
        if (firstKeptEvent == null) firstKeptEvent = "${event.javaClass.simpleName} ${event.path}"
      }
    }
    if (!tomlChanged && newDirectoryEvents.isEmpty()) return@AsyncFileListener null
    val reason = "VFS, $keptEvents of ${events.size} events, first $firstKeptEvent"

    object : AsyncFileListener.ChangeApplier {
      override fun afterVfsChange() {
        // Only hand the work over. A subtree load must never run inside the write action.
        val directories = newDirectoryEvents.mapNotNullTo(LinkedHashSet()) { it.directoryToLoad() }
        onChange(RebuildRequest(directories, reason))
      }
    }
  }, parentDisposable)
}

/**
 * Loads [directories] into the VFS, so the filename index knows every `pyproject.toml` below them.
 *
 * A VFS event arrives for the parent directory only, and a new directory has no loaded children.
 * A nested `pyproject.toml` is therefore invisible to the filename index until this load runs (PY-91841).
 * The cost is bounded by the new subtrees, not by the project.
 *
 * The visitor prunes the same names as the search, it stops at a directory of [excludedPaths], and it also
 * stops at a virtualenv. `AsyncFileListener.ChangeApplier.afterVfsChange` warns that a load of an excluded
 * file costs performance, and a build directory such as `out` carries no name that the prune list knows.
 *
 * The visitor must never follow a symbolic link. The default of [VirtualFileVisitor] is to follow one, and a
 * link into a build cache can hold a copy of the whole project. The search rejects every file of such a copy,
 * so the load would be pure waste.
 */
internal suspend fun loadSubtreesIntoVfs(directories: Set<VirtualFile>, excludedPaths: Set<Path> = emptySet()) {
  if (directories.isEmpty()) return
  withContext(Dispatchers.IO) {
    val virtualEnvReader = VirtualEnvReader()
    for (directory in directories) {
      coroutineContext.ensureActive()
      if (!directory.isValid || !directory.isDirectory) continue
      val rootPath = directory.toNioPathOrNull() ?: continue
      if (generateSequence(rootPath) { it.parent }.any { it in excludedPaths }) continue
      val layout = virtualEnvReader.getLayout(rootPath)
      log.debug { "Loading $directory into the VFS" }
      VfsUtilCore.visitChildrenRecursively(directory, object : VirtualFileVisitor<Unit>(NO_FOLLOW_SYMLINKS) {
        // Keep every rule below at or wider than `PyProjectTomlPathFilter.isVisible` of the search. A rule
        // that prunes more hides a `pyproject.toml` from the search, and the model loses that module. The
        // name rule comes from [isPrunedName], which the search reads too.
        override fun visitFile(file: VirtualFile): Boolean {
          if (!file.isDirectory) return true
          if (file.name.isPrunedName()) return false
          val path = file.toNioPathOrNull() ?: return true
          if (path in excludedPaths) return false
          // A directory that holds a python interpreter. The name of such a directory has no dot, so only
          // this check stops an install of thousands of files. The rule is wider than a virtualenv: it also
          // holds for a conda environment and for the root of a python installation.
          //
          // The walk loaded the names of the children already, and the method that takes them reads the
          // filesystem only for a directory that those names allow (PY-91841).
          return virtualEnvReader.findPythonUsingDirectoryListing(
            directory = path,
            childNames = file.children.asSequence().map { it.name },
            layout = layout,
          ) == null
        }
      })
    }
  }
}

/** The name of a `pyproject.toml`, before or after the event. */
private fun VFileEvent.isPyProjectToml(): Boolean = when (this) {
  is VFileCreateEvent -> childName == PY_PROJECT_TOML
  is VFileCopyEvent -> newChildName == PY_PROJECT_TOML
  is VFilePropertyChangeEvent ->
    propertyName == VirtualFile.PROP_NAME && (oldValue == PY_PROJECT_TOML || newValue == PY_PROJECT_TOML)
  else -> file?.name == PY_PROJECT_TOML
}

/** A directory appears or takes a new name. Its content can be unknown to the VFS. */
private fun VFileEvent.requiresSubtreeLoad(): Boolean = when (this) {
  is VFileCreateEvent -> isDirectory
  is VFileCopyEvent -> file.isDirectory
  is VFileMoveEvent -> file.isDirectory
  is VFilePropertyChangeEvent -> propertyName == VirtualFile.PROP_NAME && file.isDirectory
  else -> false
}

/** The directory of [requiresSubtreeLoad], resolved after the change. */
private fun VFileEvent.directoryToLoad(): VirtualFile? = when (this) {
  is VFileCopyEvent -> findCreatedFile()
  else -> file
}?.takeIf { it.isValid && it.isDirectory }

/** A directory disappears now. Its `pyproject.toml` files must leave the model. */
private fun VFileEvent.removesDirectory(): Boolean = this is VFileDeleteEvent && file.isDirectory

/**
 * Rejects a change that the model never reads.
 *
 * Three cases matter:
 *
 * 1. The change is outside every root. `addAsyncFileListenerBackgroundable` is application wide, so the
 *    listener also sees the change of another open project, and of a directory that no project holds. Such a
 *    change must not cost a rebuild of this project.
 * 2. The change is under a pruned directory, such as `.git` or `node_modules`.
 * 3. Every rebuild writes an `.iml` file. A rebuild must not start a new rebuild.
 *
 * A move carries two parents, and only one of them has to lie under a root. A directory that arrives from
 * outside the project keeps its old parent outside every root, and the model must still see it (PY-91841).
 *
 * The walk up ends at a root. A root reached means "keep the event", and no root reached means "outside the
 * project".
 *
 * The name rule holds for a root as well, so a project whose own directory carries a dot name gets no model.
 * `loadSubtreesIntoVfs` prunes such a directory and `PyProjectTomlPathFilter` rejects a file under it, so an
 * event kept here could never reach a module. The three rules therefore agree.
 */
private class EventFilter(knownRoots: Set<Path>) {
  /**
   * The roots as a canonical path each, in a set that follows the case rule of the filesystem.
   * The set is built once per batch, so the walk up costs one lookup for each level.
   */
  private val roots: Set<String> =
    knownRoots.mapTo(CollectionFactory.createFilePathSet()) { FileUtil.toCanonicalPath(it.toString()) }

  fun isIgnored(event: VFileEvent): Boolean {
    val name = when (event) {
      is VFileCreateEvent -> event.childName
      is VFileCopyEvent -> event.newChildName
      else -> event.file?.name
    }
    if (name != null && name.isPrunedName()) {
      if (event !is VFilePropertyChangeEvent || event.propertyName != VirtualFile.PROP_NAME ||
          event.newValue.toString().isPrunedName()) return true
    }
    // Give a new kind of event its own branch when the kind changes the parent of a file. The branch below
    // reads the parent of the file, which is the parent before the change.
    return when (event) {
      is VFileCreateEvent -> isOutside(event.parent)
      is VFileCopyEvent -> isOutside(event.newParent)
      // `VFileMoveEvent.getFile().getParent()` is the old parent, because the event is prepared before the
      // change. A directory moved into the project needs the new parent to reach a root.
      is VFileMoveEvent -> isOutside(event.oldParent) && isOutside(event.newParent)
      else -> isOutside(event.file?.parent)
    }
  }

  private fun isOutside(parent: VirtualFile?): Boolean {
    var current: VirtualFile? = parent
    while (current != null) {
      val directory = current
      // The name comes first, so the rule holds for a root too. See the note on this class.
      if (directory.name.isPrunedName()) return true
      if (directory.path in roots) return false
      current = directory.parent
    }
    return true
  }
}
