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
package org.jetbrains.settingsRepository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeProvider2
import com.intellij.openapi.vcs.merge.MultipleFileMergeDialog
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class BaseRepositoryManager(protected val dir: Path) : RepositoryManager {
  protected val lock: ReentrantReadWriteLock = ReentrantReadWriteLock()

  override fun processChildren(path: String, filter: (name: String) -> Boolean, processor: (name: String, inputStream: InputStream) -> Boolean) {
    dir.resolve(path).directoryStreamIfExists {
      for (file in it) {
        if (file.isDirectory() || file.isHidden()) {
          continue;
        }

        // we ignore empty files as well - delete if corrupted
        if (file.size() == 0L) {
          if (file.exists()) {
            try {
              LOG.warn("File $path is empty (length 0), will be removed")
              delete(file, path)
            }
            catch (e: Exception) {
              LOG.error(e)
            }
          }
          continue;
        }

        if (!processor(file.fileName.toString(), file.inputStream())) {
          break;
        }
      }
    }
  }

  override fun deleteRepository() {
    dir.deleteRecursively()
  }

  protected open fun isPathIgnored(path: String): Boolean = false

  override fun read(path: String): InputStream? {
    if (isPathIgnored(path)) {
      LOG.debug { "$path is ignored" }
      return null
    }

    var fileToDelete: Path? = null
    lock.read {
      val file = dir.resolve(path)
      when (file.sizeOrNull()) {
        -1L -> return null
        0L -> {
          // we ignore empty files as well - delete if corrupted
          fileToDelete = file
        }
        else -> return file.inputStream()
      }
    }

    try {
      lock.write {
        if (fileToDelete!!.sizeOrNull() == 0L) {
          LOG.warn("File $path is empty (length 0), will be removed")
          delete(fileToDelete!!, path)
        }
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    return null
  }

  override fun write(path: String, content: ByteArray, size: Int): Boolean {
    if (isPathIgnored(path)) {
      LOG.debug { "$path is ignored" }
      return false
    }

    LOG.debug { "Write $path" }

    try {
      lock.write {
        val file = dir.resolve(path)
        file.write(content, 0, size)
        addToIndex(file, path, content, size)
      }
    }
    catch (e: Exception) {
      LOG.error(e)
      return false
    }
    return true
  }

  /**
   * path relative to repository root
   */
  protected abstract fun addToIndex(file: Path, path: String, content: ByteArray, size: Int)

  override fun delete(path: String) {
    LOG.debug { "Remove $path"}

    lock.write {
      val file = dir.resolve(path)
      // delete could be called for non-existent file
      if (file.exists()) {
        delete(file, path)
      }
    }
  }

  private fun delete(file: Path, path: String) {
    val isFile = file.isFile()
    file.removeWithParentsIfEmpty(dir, isFile)
    deleteFromIndex(path, isFile)
  }

  protected abstract fun deleteFromIndex(path: String, isFile: Boolean)

  override fun has(path: String) = lock.read { dir.resolve(path).exists() }
}

fun Path.removeWithParentsIfEmpty(root: Path, isFile: Boolean = true) {
  delete()

  if (isFile) {
    // remove empty directories
    var parent = this.parent
    while (parent != null && parent != root) {
      parent.delete()
      parent = parent.parent
    }
  }
}

var conflictResolver: ((files: List<VirtualFile>, mergeProvider: MergeProvider2) -> Unit)? = null

fun resolveConflicts(files: List<VirtualFile>, mergeProvider: MergeProvider2): List<VirtualFile> {
  if (ApplicationManager.getApplication()!!.isUnitTestMode) {
    if (conflictResolver == null) {
      throw CannotResolveConflictInTestMode()
    }
    else {
      conflictResolver!!(files, mergeProvider)
    }
    return files
  }

  var processedFiles: List<VirtualFile>? = null
  invokeAndWaitIfNeed {
    val fileMergeDialog = MultipleFileMergeDialog(null, files, mergeProvider, object : MergeDialogCustomizer() {
      override fun getMultipleFileDialogTitle() = "Settings Repository: Files Merged with Conflicts"
    })
    fileMergeDialog.show()
    processedFiles = fileMergeDialog.processedFiles
  }
  return processedFiles!!
}

class RepositoryVirtualFile(private val path: String) : LightVirtualFile(PathUtilRt.getFileName(path), StdFileTypes.XML, "", CharsetToolkit.UTF8_CHARSET, 1L) {
  var content: ByteArray? = null
    private set

  override fun getPath() = path

  override fun setBinaryContent(content: ByteArray, newModificationStamp: Long, newTimeStamp: Long, requestor: Any?) {
    this.content = content
  }

  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
    throw IllegalStateException("You must use setBinaryContent")
  }

  override fun setContent(requestor: Any?, content: CharSequence, fireEvent: Boolean) {
    throw IllegalStateException("You must use setBinaryContent")
  }
}