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
    for (e in edits) {
      var entryIndex = cache.findEntry(e.path, e.path.size)
      val missing = entryIndex < 0
      if (entryIndex < 0) {
        entryIndex = -(entryIndex + 1)
      }
      val count = Math.min(entryIndex, maxIndex) - lastIndex
      if (count > 0) {
        fastKeep(lastIndex, count)
      }
      lastIndex = if (missing) entryIndex else cache.nextEntry(entryIndex)

      if (e is DeleteFile) {
        continue
      }
      if (e is DeleteDirectory) {
        lastIndex = cache.nextEntry(e.path, e.path.size, entryIndex)
        continue
      }

      if (missing) {
        val entry = DirCacheEntry(e.path)
        e.apply(entry, repository)
        if (entry.getRawMode() == 0) {
          throw IllegalArgumentException(MessageFormat.format(JGitText.get().fileModeNotSetForPath, entry.getPathString()))
        }
        fastAdd(entry)
      }
      else {
        // Apply to all entries of the current path (different stages)
        for (i in entryIndex..lastIndex - 1) {
          val ent = cache.getEntry(i)
          e.apply(ent, repository)
          fastAdd(ent)
        }
      }
    }

    val cnt = maxIndex - lastIndex
    if (cnt > 0) {
      fastKeep(lastIndex, cnt)
    }
  }
}

public abstract class PathEdit(val path: ByteArray) {
  public abstract fun apply(entry: DirCacheEntry, repository: Repository)
}

fun DeleteFile(path: String) = DeleteFile(Constants.encode(path))

public class DeleteFile(path: ByteArray) : PathEdit(path) {
  override fun apply(entry: DirCacheEntry, repository: Repository) = throw UnsupportedOperationException(JGitText.get().noApplyInDelete)
}

public class DeleteDirectory(entryPath: String) : PathEdit(Constants.encode(if (entryPath.endsWith("/") || entryPath.length() == 0) entryPath else entryPath + "/")) {
  override fun apply(entry: DirCacheEntry, repository: Repository) = throw UnsupportedOperationException(JGitText.get().noApplyInDelete)
}
