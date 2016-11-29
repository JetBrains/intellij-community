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
import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeProvider2
import com.intellij.openapi.vcs.merge.MultipleFileMergeDialog
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.io.*
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class BaseRepositoryManager(protected val dir: Path) : RepositoryManager {
  protected val lock: ReentrantReadWriteLock = ReentrantReadWriteLock()

  override fun processChildren(path: String, filter: (name: String) -> Boolean, processor: (name: String, inputStream: InputStream) -> Boolean) {
    dir.resolve(path).directoryStreamIfExists({ filter(it.fileName.toString()) }) {
      for (file in it) {
        val attributes: BasicFileAttributes?
        try {
          attributes = file.basicAttributesIfExists()
        }
        catch (e: IOException) {
          LOG.warn(e)
          continue
        }

        if (attributes == null || attributes.isDirectory || file.isHidden()) {
          continue
        }

        // we ignore empty files as well - delete if corrupted
        if (attributes.size() == 0L) {
          LOG.catchAndLog {
            LOG.warn("File $path is empty (length 0), will be removed")
            delete(file, path)
          }
          continue
        }

        if (!file.inputStream().use { processor(file.fileName.toString(), it) }) {
          break
        }
      }
    }
  }

  override fun deleteRepository() {
    dir.delete()
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

    LOG.catchAndLog {
      if (fileToDelete!!.sizeOrNull() == 0L) {
        LOG.warn("File $path is empty (length 0), will be removed")
        delete(fileToDelete!!, path)
      }
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

  override fun delete(path: String): Boolean {
    LOG.debug { "Remove $path"}
    return delete(dir.resolve(path), path)
  }

  private fun delete(file: Path, path: String): Boolean {
    val fileAttributes = file.basicAttributesIfExists() ?: return false
    val isFile = fileAttributes.isRegularFile
    if (!file.deleteWithParentsIfEmpty(dir, isFile)) {
      return false
    }

    lock.write {
      deleteFromIndex(path, isFile)
    }
    return true
  }

  protected abstract fun deleteFromIndex(path: String, isFile: Boolean)

  override fun has(path: String) = lock.read { dir.resolve(path).exists() }
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

  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw IllegalStateException("You must use setBinaryContent")

  override fun setContent(requestor: Any?, content: CharSequence, fireEvent: Boolean) = throw IllegalStateException("You must use setBinaryContent")
}