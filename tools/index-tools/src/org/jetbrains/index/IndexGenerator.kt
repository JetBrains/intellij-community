// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index

import com.google.common.hash.HashCode
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.stubs.FileContentHashing
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.PersistentHashMap
import junit.framework.TestCase
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author traff
 */

abstract class IndexGenerator<Value>(private val indexStorageFilePath: String) {
  companion object {
    val CHECK_HASH_COLLISIONS: Boolean = System.getenv("INDEX_GENERATOR_CHECK_HASH_COLLISIONS")?.toBoolean() ?: false
  }

  open val fileFilter: VirtualFileFilter
    get() = VirtualFileFilter { f -> !f.isDirectory }

  data class Stats(val indexed: AtomicInteger, val skipped: AtomicInteger) {
    constructor() : this(AtomicInteger(0), AtomicInteger(0))
  }

  protected fun buildIndexForRoots(roots: List<VirtualFile>) {
    val hashing = FileContentHashing()

    val storage = createStorage(indexStorageFilePath)

    println("Writing indices to ${storage.baseFile.absolutePath}")

    try {
      val map = HashMap<HashCode, Pair<String, Value>>()

      for (file in roots) {
        println("Processing files in root ${file.path}")
        val stats = Stats()
        VfsUtilCore.visitChildrenRecursively(file,
                                             object : VirtualFileVisitor<Boolean>() {
                                               override fun visitFile(file: VirtualFile): Boolean {
                                                 return indexFile(file, hashing, map, storage, stats)
                                               }
                                             })

        println("${stats.indexed.get()} entries written, ${stats.skipped.get()} skipped")
      }
    }
    finally {
      storage.close()
    }
  }

  private fun indexFile(file: VirtualFile,
                        hashing: FileContentHashing,
                        map: HashMap<HashCode, Pair<String, Value>>,
                        storage: PersistentHashMap<HashCode, Value>,
                        stats: Stats): Boolean {
    try {
      if (fileFilter.accept(file)) {
        val fileContent = FileContentImpl(
          file, file.contentsToByteArray())

        val hashCode = hashing.hashString(fileContent)

        val value = getIndexValue(fileContent)

        if (value != null) {
          val item = map[hashCode]
          if (item == null) {
            storage.put(hashCode, value)

            stats.indexed.incrementAndGet()

            if (CHECK_HASH_COLLISIONS) {
              map.put(hashCode,
                      Pair(fileContent.contentAsText.toString(), value))
            }
          }
          else {
            TestCase.assertEquals(item.first,
                                  fileContent.contentAsText.toString())
            TestCase.assertTrue(value == item.second)
          }
        }
        else {
          stats.skipped.incrementAndGet()
        }
      }
    }
    catch (e: NoSuchElementException) {
      return false
    }

    return true
  }

  protected abstract fun getIndexValue(fileContent: FileContentImpl): Value?

  protected abstract fun createStorage(stubsStorageFilePath: String): PersistentHashMap<HashCode, Value>
}