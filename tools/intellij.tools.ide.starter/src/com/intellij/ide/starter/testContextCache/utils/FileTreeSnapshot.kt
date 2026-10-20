package com.intellij.ide.starter.testContextCache.utils

import com.intellij.openapi.util.io.NioFiles
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.io.sha256Hex
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo

@ApiStatus.Internal
class FileTreeSnapshot private constructor(
  private val root: Path,
  private val rootExisted: Boolean,
  val snapshotDir: Path,
  private val snapshotEntries: Map<Path, SnapshotEntry>,
  private val snapshotDirectories: Set<Path>,
  private val shouldTrackDirectory: (Path) -> Boolean,
  private val shouldTrackFile: (Path) -> Boolean,
) : AutoCloseable {
  val trackedFilesCount: Int
    get() = snapshotEntries.size

  val trackedDirectoriesCount: Int
    get() = snapshotDirectories.size

  /** The tracked files that this snapshot reads from its base snapshot, and does not copy. */
  val sharedFilesCount: Int
    get() = snapshotEntries.values.count { !it.snapshotPath.startsWith(snapshotDir) }

  fun hasChanges(currentRoot: Path = root): Boolean = changes(currentRoot).hasChanges()

  fun changes(currentRoot: Path = root): FileTreeChanges {
    val normalizedCurrentRoot = currentRoot.toAbsolutePath().normalize()
    val rootChanged = normalizedCurrentRoot.exists() != rootExisted || normalizedCurrentRoot.exists() && !normalizedCurrentRoot.isDirectory()
    val currentPaths = collectTrackedPaths(normalizedCurrentRoot, shouldTrackDirectory, shouldTrackFile)
    val currentRelativeFiles = currentPaths.files.toHashSet()
    val currentRelativeDirectories = currentPaths.directories.toHashSet()
    val modifiedFiles = LinkedHashSet<Path>()
    val deletedFiles = LinkedHashSet<Path>()
    val newFiles = LinkedHashSet<Path>()

    currentPaths.files.forEach { relativePath ->
      val entry = snapshotEntries[relativePath]
      if (entry == null) {
        normalizedCurrentRoot.resolve(relativePath).logContentChange("file exists in current state but not in the snapshot")
        newFiles.add(relativePath)
        return@forEach
      }
      val currentHash = try {
        sha256Hex(normalizedCurrentRoot.resolve(relativePath))
      }
      catch (_: NoSuchFileException) {
        // The file disappeared after the walk, so the check below reports it as deleted.
        currentRelativeFiles.remove(relativePath)
        return@forEach
      }
      if (currentHash != entry.hash) {
        normalizedCurrentRoot.resolve(relativePath).logContentChange("file content differs from the snapshot")
        modifiedFiles.add(relativePath)
      }
    }

    snapshotEntries.keys
      .filterTo(deletedFiles) { it !in currentRelativeFiles }
      .forEach { relativePath ->
        normalizedCurrentRoot.resolve(relativePath).logContentChange("file exists in the snapshot but not in current state")
      }

    val newDirectories = currentRelativeDirectories - snapshotDirectories
    val deletedDirectories = snapshotDirectories - currentRelativeDirectories
    newDirectories.forEach { relativePath ->
      normalizedCurrentRoot.resolve(relativePath).logContentChange("directory exists in current state but not in the snapshot")
    }
    deletedDirectories.forEach { relativePath ->
      normalizedCurrentRoot.resolve(relativePath).logContentChange("directory exists in the snapshot but not in current state")
    }

    return FileTreeChanges(
      rootChanged = rootChanged,
      modifiedFiles = modifiedFiles,
      deletedFiles = deletedFiles,
      newFiles = newFiles,
      newDirectories = newDirectories,
      deletedDirectories = deletedDirectories,
    )
  }

  private fun restoreFile(relativePath: Path, targetRoot: Path) {
    val entry = snapshotEntries[relativePath] ?: error("Snapshot file is not tracked: ${relativePath.invariantSeparatorsPathString}")
    val targetPath = targetRoot.resolve(relativePath)
    targetPath.parent?.createDirectories()
    // On Windows, REPLACE_EXISTING fails on a read-only target, such as a Git object. NioFiles deletes it.
    NioFiles.deleteRecursively(targetPath)
    // COPY_ATTRIBUTES preserves file modification times, so mtime-sensitive tests (e.g. Project View
    // "Sort by Modification Time") behave the same on a restored file as on a fresh unpack.
    Files.copy(entry.snapshotPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  }

  private fun restoreDirectory(relativePath: Path, targetRoot: Path) {
    targetRoot.resolve(relativePath).createDirectories()
  }

  /**
   * Restores from an already computed [changes], so a caller that has just inspected the tree does not walk and
   * hash it twice.
   */
  fun restoreTracked(targetRoot: Path, changes: FileTreeChanges) {
    if (changes.rootChanged) {
      restoreAll(targetRoot)
      return
    }

    val normalizedTargetRoot = targetRoot.toAbsolutePath().normalize()
    changes.newFiles.forEach { relativePath ->
      NioFiles.deleteRecursively(normalizedTargetRoot.resolve(relativePath))
    }
    changes.newDirectories
      .sortedByDescending { it.nameCount }
      .forEach { relativePath ->
        NioFiles.deleteRecursively(normalizedTargetRoot.resolve(relativePath))
      }
    changes.modifiedFiles.forEach { relativePath ->
      restoreFile(relativePath, normalizedTargetRoot)
    }
    changes.deletedFiles.forEach { relativePath ->
      restoreFile(relativePath, normalizedTargetRoot)
    }
    changes.deletedDirectories.forEach { relativePath ->
      restoreDirectory(relativePath, normalizedTargetRoot)
    }
  }

  /** Rebuilds [targetRoot] from the snapshot. An entry can live in the base snapshot, so each file comes from its entry. */
  fun restoreAll(targetRoot: Path = root) {
    val normalizedTargetRoot = targetRoot.toAbsolutePath().normalize()
    NioFiles.deleteRecursively(normalizedTargetRoot)
    if (!rootExisted) {
      return
    }
    normalizedTargetRoot.createDirectories()
    snapshotDirectories.forEach { relativePath ->
      restoreDirectory(relativePath, normalizedTargetRoot)
    }
    snapshotEntries.keys.forEach { relativePath ->
      restoreFile(relativePath, normalizedTargetRoot)
    }
  }

  /** Deletes the copies of this snapshot. It leaves the files that it shares with its base snapshot. */
  override fun close() {
    NioFiles.deleteRecursively(snapshotDir)
  }

  /**
   * Reports one difference from the baseline at output level, not error level.
   *
   * A difference is the normal path of every recovery. Error level would read as a CI failure.
   */
  private fun Path.logContentChange(reason: String) {
    logOutput("Detected changed file-tree path: $this ($reason)")
  }

  data class FileTreeChanges(
    val rootChanged: Boolean = false,
    val modifiedFiles: Set<Path> = emptySet(),
    val deletedFiles: Set<Path> = emptySet(),
    val newFiles: Set<Path> = emptySet(),
    val newDirectories: Set<Path> = emptySet(),
    val deletedDirectories: Set<Path> = emptySet(),
  ) {
    fun hasChanges(): Boolean {
      return rootChanged ||
             modifiedFiles.isNotEmpty() ||
             deletedFiles.isNotEmpty() ||
             newFiles.isNotEmpty() ||
             newDirectories.isNotEmpty() ||
             deletedDirectories.isNotEmpty()
    }
  }

  companion object {
    /**
     * @param shouldTrackDirectory whether to track the directory at a relative path, with everything under it.
     * @param shouldTrackFile whether to track the file at a relative path. An untracked file is never restored.
     * @param base an earlier snapshot of the same root. A file with the same content in [base] is not copied again,
     * and the new snapshot reads it from [base]. So [base] must stay open while the new snapshot is in use.
     */
    fun capture(
      root: Path,
      shouldTrackDirectory: (Path) -> Boolean,
      shouldTrackFile: (Path) -> Boolean = { true },
      base: FileTreeSnapshot? = null,
    ): FileTreeSnapshot {
      val normalizedRoot = root.toAbsolutePath().normalize()
      require(base == null || base.root == normalizedRoot) { "The base snapshot is of ${base?.root}, not of $normalizedRoot" }
      val snapshotDir = Files.createTempDirectory("ui-test-file-tree-snapshot-")
      val rootExisted = normalizedRoot.exists()
      val trackedPaths = collectTrackedPaths(normalizedRoot, shouldTrackDirectory, shouldTrackFile)
      val snapshotEntries = LinkedHashMap<Path, SnapshotEntry>()
      trackedPaths.files.forEach { relativePath ->
        val file = normalizedRoot.resolve(relativePath)
        try {
          val baseEntry = base?.snapshotEntries?.get(relativePath)
          if (baseEntry != null && sha256Hex(file) == baseEntry.hash) {
            snapshotEntries[relativePath] = baseEntry
            return@forEach
          }
          val snapshotPath = snapshotDir.resolve(relativePath)
          snapshotPath.parent?.createDirectories()
          Files.copy(file, snapshotPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
          // Hash the copy, not the source, because the source can disappear after the copy.
          snapshotEntries[relativePath] = SnapshotEntry(snapshotPath = snapshotPath, hash = sha256Hex(snapshotPath))
        }
        catch (_: NoSuchFileException) {
          // The IDE can delete a file between the walk above and this copy. It writes `.idea/.gitignore` while a
          // project opens, for one, and leaves a short-lived `.gitignore~` backup. A test that holds such a file at
          // capture time must not fail, so the file stays untracked. It is gone already, and a restore deletes it
          // again if it comes back.
          logOutput("Skipped a file that disappeared during the snapshot: ${relativePath.invariantSeparatorsPathString}")
        }
      }

      return FileTreeSnapshot(
        root = normalizedRoot,
        rootExisted = rootExisted,
        snapshotDir = snapshotDir,
        snapshotEntries = snapshotEntries,
        snapshotDirectories = trackedPaths.directories.toSet(),
        shouldTrackDirectory = shouldTrackDirectory,
        shouldTrackFile = shouldTrackFile,
      )
    }
  }
}

private data class SnapshotEntry(
  val snapshotPath: Path,
  val hash: String,
)

private data class TrackedPaths(
  val files: List<Path>,
  val directories: List<Path>,
)

private fun collectTrackedPaths(
  root: Path,
  shouldTrackDirectory: (Path) -> Boolean,
  shouldTrackFile: (Path) -> Boolean,
): TrackedPaths {
  val trackedFiles = ArrayList<Path>()
  val trackedDirectories = ArrayList<Path>()
  if (!root.exists()) {
    return TrackedPaths(trackedFiles, trackedDirectories)
  }
  if (!root.isDirectory()) {
    return TrackedPaths(trackedFiles, trackedDirectories)
  }

  Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (dir != root) {
        val relativePath = dir.relativeTo(root)
        if (!shouldTrackDirectory(relativePath)) {
          return FileVisitResult.SKIP_SUBTREE
        }
        trackedDirectories.add(relativePath)
      }
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val relativePath = file.relativeTo(root)
      if (attrs.isRegularFile && shouldTrackFile(relativePath)) {
        trackedFiles.add(relativePath)
      }
      return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
      // The running IDE can delete an entry after the walk lists it. The entry is gone, so it stays untracked.
      if (exc is NoSuchFileException) {
        return FileVisitResult.CONTINUE
      }
      throw exc
    }
  })

  return TrackedPaths(trackedFiles, trackedDirectories)
}
