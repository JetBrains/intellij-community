// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.async.coroutineDispatchingContext
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeProvider2
import com.intellij.openapi.vcs.merge.MultipleFileMergeDialog
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.io.*
import kotlinx.coroutines.withContext
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
    dir.resolve(path).directoryStreamIfExists({ filter(it.fileName.toString()) }) { fileStream ->
      for (file in fileStream) {
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
          LOG.runAndLogException {
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

  override fun <R> read(path: String, consumer: (InputStream?) -> R): R {
    var fileToDelete: Path? = null
    lock.read {
      val file = dir.resolve(path)
      when (file.sizeOrNull()) {
        -1L -> return consumer(null)
        0L -> {
          // we ignore empty files as well - delete if corrupted
          fileToDelete = file
        }
        else -> return file.inputStream().use(consumer)
      }
    }

    LOG.runAndLogException {
      if (fileToDelete!!.sizeOrNull() == 0L) {
        LOG.warn("File $path is empty (length 0), will be removed")
        delete(fileToDelete!!, path)
      }
    }
    return consumer(null)
  }

  override fun write(path: String, content: ByteArray, size: Int): Boolean {
    LOG.debug { "Write $path" }

    try {
      lock.write {
        val file = dir.resolve(path)
        file.write(content, 0, size)
        if (isPathIgnored(path)) {
          LOG.debug { "$path is ignored and will be not added to index" }
        }
        else {
          addToIndex(file, path, content, size)
        }
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

    if (!isPathIgnored(path)) {
      lock.write {
        deleteFromIndex(path, isFile)
      }
    }
    return true
  }

  protected abstract fun deleteFromIndex(path: String, isFile: Boolean)

  override fun has(path: String): Boolean = lock.read { dir.resolve(path).exists() }
}

var conflictResolver: ((files: List<VirtualFile>, mergeProvider: MergeProvider2) -> Unit)? = null

suspend fun resolveConflicts(files: List<VirtualFile>, mergeProvider: MergeProvider2): List<VirtualFile> {
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
  withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
    val fileMergeDialog = MultipleFileMergeDialog(null, files, mergeProvider, object : MergeDialogCustomizer() {
      override fun getMultipleFileDialogTitle() = "Settings Repository: Conflicts"
    })
    fileMergeDialog.show()
    processedFiles = fileMergeDialog.processedFiles
  }
  return processedFiles!!
}

class RepositoryVirtualFile(private val path: String) : LightVirtualFile(PathUtilRt.getFileName(path), StdFileTypes.XML, "", CharsetToolkit.UTF8_CHARSET, 1L) {
  var byteContent: ByteArray? = null
    private set

  override fun getPath(): String = path

  override fun setBinaryContent(content: ByteArray, newModificationStamp: Long, newTimeStamp: Long, requestor: Any?) {
    this.byteContent = content
  }

  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): Nothing = throw IllegalStateException("You must use setBinaryContent")

  override fun setContent(requestor: Any?, content: CharSequence, fireEvent: Boolean): Nothing = throw IllegalStateException("You must use setBinaryContent")
}