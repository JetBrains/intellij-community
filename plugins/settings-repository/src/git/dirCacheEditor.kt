// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.deleteWithParentsIfEmpty
import com.intellij.util.io.toByteArray
import org.eclipse.jgit.dircache.BaseDirCacheEditor
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.Repository
import java.io.File
import java.io.FileInputStream
import java.text.MessageFormat
import kotlin.io.path.exists
import kotlin.math.min

private val EDIT_CMP = Comparator<PathEdit> { o1, o2 ->
  val a = o1.path
  val b = o2.path
  DirCache.cmp(a, a.size, b, b.size)
}

/**
 * Don't copy edits,
 * DeletePath (renamed to DeleteFile) accepts raw path
 * Pass repository to apply
 */
class DirCacheEditor(edits: List<PathEdit>, private val repository: Repository, dirCache: DirCache, estimatedNumberOfEntries: Int) : BaseDirCacheEditor(dirCache, estimatedNumberOfEntries) {
  private val edits = edits.sortedWith(EDIT_CMP)

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
    val maxIndex = cache.entryCount
    var lastIndex = 0
    for (edit in edits) {
      var entryIndex = cache.findEntry(edit.path, edit.path.size)
      val missing = entryIndex < 0
      if (entryIndex < 0) {
        entryIndex = -(entryIndex + 1)
      }
      val count = min(entryIndex, maxIndex) - lastIndex
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
        if (entry.rawMode == 0) {
          throw IllegalArgumentException(MessageFormat.format(JGitText.get().fileModeNotSetForPath, entry.pathString))
        }
        fastAdd(entry)
      }
      else if (edit is AddFile || edit is AddLoadedFile) {
        // apply to first entry and remove others
        val firstEntry = cache.getEntry(entryIndex)
        val entry: DirCacheEntry
        if (firstEntry.isMerged) {
          entry = firstEntry
        }
        else {
          entry = DirCacheEntry(edit.path)
          entry.creationTime = firstEntry.creationTime
        }
        edit.apply(entry, repository)
        fastAdd(entry)
      }
      else {
        // apply to all entries of the current path (different stages)
        for (i in entryIndex until lastIndex) {
          val entry = cache.getEntry(i)
          edit.apply(entry, repository)
          fastAdd(entry)
        }
      }
    }

    val count = maxIndex - lastIndex
    if (count > 0) {
      fastKeep(lastIndex, count)
    }
  }
}

interface PathEdit {
  val path: ByteArray

  fun apply(entry: DirCacheEntry, repository: Repository)
}

abstract class PathEditBase(final override val path: ByteArray) : PathEdit

private fun encodePath(path: String): ByteArray {
  val bytes = Charsets.UTF_8.encode(path).toByteArray()
  if (SystemInfo.isWindows) {
    for (i in bytes.indices) {
      if (bytes[i].toInt().toChar() == '\\') {
        bytes[i] = '/'.code.toByte()
      }
    }
  }
  return bytes
}

class AddFile(private val pathString: String) : PathEditBase(encodePath(pathString)) {
  override fun apply(entry: DirCacheEntry, repository: Repository) {
    val file = File(repository.workTree, pathString)
    entry.fileMode = FileMode.REGULAR_FILE
    val length = file.length()
    entry.setLength(length)
    entry.lastModified = file.lastModified()

    val input = FileInputStream(file)
    val inserter = repository.newObjectInserter()
    try {
      entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, length, input))
      inserter.flush()
    }
    finally {
      inserter.close()
      input.close()
    }
  }
}

class AddLoadedFile(path: String, private val content: ByteArray, private val lastModified: Long = System.currentTimeMillis()) : PathEditBase(
    encodePath(path)) {
  override fun apply(entry: DirCacheEntry, repository: Repository) {
    entry.fileMode = FileMode.REGULAR_FILE
    entry.length = content.size
    entry.lastModified = lastModified

    val inserter = repository.newObjectInserter()
    inserter.use {
      entry.setObjectId(it.insert(Constants.OBJ_BLOB, content))
      it.flush()
    }
  }
}

fun DeleteFile(path: String): DeleteFile = DeleteFile(encodePath(path))

class DeleteFile(path: ByteArray) : PathEditBase(path) {
  override fun apply(entry: DirCacheEntry, repository: Repository) = throw UnsupportedOperationException(JGitText.get().noApplyInDelete)
}

class DeleteDirectory(entryPath: String) : PathEditBase(
    encodePath(if (entryPath.endsWith('/') || entryPath.isEmpty()) entryPath else "$entryPath/")) {
  override fun apply(entry: DirCacheEntry, repository: Repository) = throw UnsupportedOperationException(JGitText.get().noApplyInDelete)
}

fun Repository.edit(edit: PathEdit) {
  edit(listOf(edit))
}

fun Repository.edit(edits: List<PathEdit>) {
  if (edits.isEmpty()) {
    return
  }

  val dirCache = lockDirCache()
  try {
    DirCacheEditor(edits, this, dirCache, dirCache.entryCount + 4).commit()
  }
  finally {
    dirCache.unlock()
  }
}

private class DirCacheTerminator(dirCache: DirCache) : BaseDirCacheEditor(dirCache, 0) {
  override fun finish() {
    replace()
  }
}

fun Repository.deleteAllFiles(deletedSet: MutableSet<String>? = null, fromWorkingTree: Boolean = true) {
  val dirCache = lockDirCache()
  try {
    if (deletedSet != null) {
      for (i in 0 until dirCache.entryCount) {
        val entry = dirCache.getEntry(i)
        if (entry.fileMode == FileMode.REGULAR_FILE) {
          deletedSet.add(entry.pathString)
        }
      }
    }
    DirCacheTerminator(dirCache).commit()
  }
  finally {
    dirCache.unlock()
  }

  if (fromWorkingTree) {
    val files = workTree.listFiles { file -> file.name != Constants.DOT_GIT }
    if (files != null) {
      for (file in files) {
        FileUtil.delete(file)
      }
    }
  }
}

fun Repository.writePath(path: String, bytes: ByteArray) {
  edit(AddLoadedFile(path, bytes))
  FileUtil.writeToFile(File(workTree, path), bytes)
}

fun Repository.deletePath(path: String, isFile: Boolean = true, fromWorkingTree: Boolean = true) {
  edit((if (isFile) DeleteFile(path) else DeleteDirectory(path)))

  if (fromWorkingTree) {
    val workTree = workTree.toPath()
    val ioFile = workTree.resolve(path)
    if (ioFile.exists()) {
      ioFile.deleteWithParentsIfEmpty(workTree, isFile)
    }
  }
}