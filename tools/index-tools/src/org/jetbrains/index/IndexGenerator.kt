// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index

import com.google.common.hash.HashCode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.index.id.SingleIndexGeneratorImpl
import org.jetbrains.index.stubs.StubsGenerator
import sun.rmi.rmic.iiop.StubGenerator
import java.io.IOException

/**
 * @author traff
 */

enum class FileIndexingResult {
  INDEXED, SKIPPED, EXCEPTION
}

abstract class IndexGenerator {
  abstract val internalName: String

  @Throws(IOException::class)
  abstract fun openStorage(indexStorageFilePath: String)

  @Throws(IOException::class)
  abstract fun closeStorage()

  abstract fun indexFile(file: VirtualFile, content: FileContentImpl, fileType: FileType, hash: HashCode): FileIndexingResult

  open val fileFilter: VirtualFileFilter
    get() = VirtualFileFilter { f -> !f.isDirectory }
}

abstract class SingleIndexGenerator<Value>: IndexGenerator() {
  var storage: PersistentHashMap<HashCode, Value>? = null

  protected abstract fun getIndexValue(fileContent: FileContentImpl): Value?

  protected abstract fun createStorage(storageFilePath: String): PersistentHashMap<HashCode, Value>

  @Throws(IOException::class)
  override fun openStorage(indexStorageFilePath: String) {
    storage = createStorage(indexStorageFilePath)
    println("Writing indices to ${storage!!.baseFile.absolutePath}")
  }

  @Throws(IOException::class)
  override fun closeStorage() {
    storage?.close()
  }

  override fun indexFile(file: VirtualFile, content: FileContentImpl, fileType: FileType, hash: HashCode): FileIndexingResult {
    try {
      val value = getIndexValue(content) ?: return FileIndexingResult.SKIPPED
      storage!!.put(hash, value)
      return FileIndexingResult.INDEXED
    }
    catch (e: Exception) {
      e.printStackTrace()
      return FileIndexingResult.EXCEPTION
    }
  }
}

fun getAllIdeIndexGenerators() : Array<SingleIndexGenerator<*>> {
  val regularIndices: Array<SingleIndexGenerator<*>> = FileBasedIndexExtension.EXTENSION_POINT_NAME.extensions.filter { it.dependsOnFileContent() && it.name != StubUpdatingIndex.INDEX_ID }.map {
    SingleIndexGeneratorImpl(it)
  }.toTypedArray()

  val registeredFileTypes = FileTypeManager.getInstance().registeredFileTypes.map { it.name }.sorted().toString()
  return regularIndices + arrayOf(StubsGenerator(registeredFileTypes))
}