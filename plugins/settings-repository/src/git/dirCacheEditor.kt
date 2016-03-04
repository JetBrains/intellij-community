/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jgit.dirCache

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.exists
import org.eclipse.jgit.dircache.BaseDirCacheEditor
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.Repository
import org.jetbrains.settingsRepository.byteBufferToBytes
import org.jetbrains.settingsRepository.removeWithParentsIfEmpty
import java.io.File
import java.io.FileInputStream
import java.text.MessageFormat
import java.util.*

private val EDIT_CMP = Comparator<org.jetbrains.jgit.dirCache.PathEdit> { o1, o2 ->
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
        if (entry.rawMode == 0) {
          throw IllegalArgumentException(MessageFormat.format(JGitText.get().fileModeNotSetForPath, entry.pathString))
        }
        fastAdd(entry)
      }
      else if (edit is AddFile || edit is AddLoadedFile) {
        // apply to first entry and remove others
        var firstEntry = cache.getEntry(entryIndex)
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
        for (i in entryIndex..lastIndex - 1) {
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

abstract class PathEditBase(override final val path: ByteArray) : PathEdit

private fun encodePath(path: String): ByteArray {
  val bytes = byteBufferToBytes(Constants.CHARSET.encode(path))
  if (SystemInfo.isWindows) {
    for (i in 0..bytes.size - 1) {
      if (bytes[i].toChar() == '\\') {
        bytes[i] = '/'.toByte()
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

class AddLoadedFile(path: String, private val content: ByteArray, private val size: Int = content.size, private val lastModified: Long = System.currentTimeMillis()) : PathEditBase(encodePath(path)) {
  override fun apply(entry: DirCacheEntry, repository: Repository) {
    entry.fileMode = FileMode.REGULAR_FILE
    entry.length = size
    entry.lastModified = lastModified

    val inserter = repository.newObjectInserter()
    try {
      entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, content, 0, size))
      inserter.flush()
    }
    finally {
      inserter.close()
    }
  }
}

fun DeleteFile(path: String) = DeleteFile(encodePath(path))

class DeleteFile(path: ByteArray) : PathEditBase(path) {
  override fun apply(entry: DirCacheEntry, repository: Repository) = throw UnsupportedOperationException(JGitText.get().noApplyInDelete)
}

class DeleteDirectory(entryPath: String) : PathEditBase(encodePath(if (entryPath.endsWith('/') || entryPath.isEmpty()) entryPath else "$entryPath/")) {
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
      for (i in 0..dirCache.entryCount - 1) {
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

fun Repository.writePath(path: String, bytes: ByteArray, size: Int = bytes.size) {
  edit(AddLoadedFile(path, bytes, size))
  FileUtil.writeToFile(File(workTree, path), bytes, 0, size)
}

fun Repository.deletePath(path: String, isFile: Boolean = true, fromWorkingTree: Boolean = true) {
  edit((if (isFile) DeleteFile(path) else DeleteDirectory(path)))

  if (fromWorkingTree) {
    val workTree = workTree.toPath()
    val ioFile = workTree.resolve(path)
    if (ioFile.exists()) {
      ioFile.removeWithParentsIfEmpty(workTree, isFile)
    }
  }
}