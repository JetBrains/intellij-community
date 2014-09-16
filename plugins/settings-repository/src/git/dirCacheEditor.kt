package org.jetbrains.jgit.dirCache

import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.internal.JGitText
import java.util.Comparator
import java.io.IOException
import java.text.MessageFormat
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.BaseDirCacheEditor
import org.eclipse.jgit.dircache.DirCacheEntry
import java.io.File
import org.eclipse.jgit.lib.FileMode
import java.io.FileInputStream
import java.util.Collections

private val EDIT_CMP = object : Comparator<PathEdit> {
  override fun compare(o1: PathEdit, o2: PathEdit): Int {
    val a = o1.path
    val b = o2.path
    return DirCache.cmp(a, a.size, b, b.size)
  }
}

/**
 * Don't copy edits,
 * DeletePath (renamed to DeleteFile) accepts raw path
 * Pass repository to apply
 */
public class DirCacheEditor(edits: List<PathEdit>, private val repository: Repository, dirCache: DirCache, estimatedNumberOfEntries: Int) : BaseDirCacheEditor(dirCache, estimatedNumberOfEntries) {
  private val edits = edits.sortBy(EDIT_CMP)

  throws(javaClass<IOException>())
  override fun commit(): Boolean {
    if (edits.isEmpty()) {
      // No changes? Don't rewrite the index.
      //
      cache.unlock()
      return true
    }
    return super.commit()
  }

  override fun finish() {
    if (!edits.isEmpty()) {
      applyEdits()
      replace()
    }
  }

  private fun applyEdits() {
    val maxIndex = cache.getEntryCount()
    var lastIndex = 0
    for (edit in edits) {
      var entryIndex = cache.findEntry(edit.path, edit.path.size)
      val missing = entryIndex < 0
      if (entryIndex < 0) {
        entryIndex = -(entryIndex + 1)
      }
      val count = Math.min(entryIndex, maxIndex) - lastIndex
      if (count > 0) {
        fastKeep(lastIndex, count)
      }
      lastIndex = if (missing) entryIndex else cache.nextEntry(entryIndex)

      if (edit is DeleteFile) {
        continue
      }
      if (edit is DeleteDirectory) {
        lastIndex = cache.nextEntry(edit.path, edit.path.size, entryIndex)
        continue
      }

      if (missing) {
        val entry = DirCacheEntry(edit.path)
        edit.apply(entry, repository)
        if (entry.getRawMode() == 0) {
          throw IllegalArgumentException(MessageFormat.format(JGitText.get().fileModeNotSetForPath, entry.getPathString()))
        }
        fastAdd(entry)
      }
      else {
        // apply to all entries of the current path (different stages)
        for (i in entryIndex..lastIndex - 1) {
          val entry = cache.getEntry(i)
          edit.apply(entry, repository)
          fastAdd(entry)

          if (edit is AddFile || edit is AddLoadedFile) {
            // apply to first entry and remove others
            break
          }
        }
      }
    }

    val count = maxIndex - lastIndex
    if (count > 0) {
      fastKeep(lastIndex, count)
    }
  }
}

public abstract class PathEdit(val path: ByteArray) {
  public abstract fun apply(entry: DirCacheEntry, repository: Repository)
}

class AddFile(private val pathString: String) : PathEdit(Constants.encode(pathString)) {
  override fun apply(entry: DirCacheEntry, repository: Repository) {
    val file = File(repository.getWorkTree(), pathString)
    entry.setFileMode(FileMode.REGULAR_FILE)
    val length = file.length()
    entry.setLength(length)
    entry.setLastModified(file.lastModified())

    val input = FileInputStream(file)
    val inserter = repository.newObjectInserter()
    try {
      entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, length, input))
      inserter.flush()
    }
    finally {
      inserter.release()
      input.close()
    }
  }
}

class AddLoadedFile(path: String, private val content: ByteArray, private val size: Int, private val lastModified: Long) : PathEdit(Constants.encode(path)) {
  override fun apply(entry: DirCacheEntry, repository: Repository) {
    entry.setFileMode(FileMode.REGULAR_FILE)
    entry.setLength(size)
    entry.setLastModified(lastModified)

    val inserter = repository.newObjectInserter()
    try {
      entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, content, 0, size))
      inserter.flush()
    }
    finally {
      inserter.release()
    }
  }
}

fun DeleteFile(path: String) = DeleteFile(Constants.encode(path))

public class DeleteFile(path: ByteArray) : PathEdit(path) {
  override fun apply(entry: DirCacheEntry, repository: Repository) = throw UnsupportedOperationException(JGitText.get().noApplyInDelete)
}

public class DeleteDirectory(entryPath: String) : PathEdit(Constants.encode(if (entryPath.endsWith("/") || entryPath.length() == 0) entryPath else entryPath + "/")) {
  override fun apply(entry: DirCacheEntry, repository: Repository) = throw UnsupportedOperationException(JGitText.get().noApplyInDelete)
}

public fun Repository.edit(edit: PathEdit) {
  edit(Collections.singletonList(edit))
}

public fun Repository.edit(edits: List<PathEdit>) {
  if (edits.isEmpty()) {
    return
  }

  val dirCache = lockDirCache()
  try {
    DirCacheEditor(edits, this, dirCache, dirCache.getEntryCount() + 4).commit()
  }
  finally {
    dirCache.unlock()
  }
}

public fun Repository.remove(path: String, isFile: Boolean) {
  edit((if (isFile) DeleteFile(path) else DeleteDirectory(path)))
}