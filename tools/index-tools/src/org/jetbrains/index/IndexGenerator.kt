// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index

import com.google.common.hash.HashCode
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.stubs.FileContentHashing
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.PersistentHashMap
import junit.framework.TestCase
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

abstract class IndexGenerator<Value>(private val indexStorageFilePath: String) {
  companion object {
    @Suppress("MemberVisibilityCanBePrivate") // used by GoLand
    const val CHECK_HASH_COLLISIONS_PROPERTY = "idea.index.generator.check.hash.collisions"
    val CHECK_HASH_COLLISIONS: Boolean = SystemProperties.`is`(CHECK_HASH_COLLISIONS_PROPERTY)
  }

  open val fileFilter: VirtualFileFilter
    get() = VirtualFileFilter { f -> !f.isDirectory }

  data class Stats(val indexed: AtomicInteger, val skipped: AtomicInteger) {
    constructor() : this(AtomicInteger(), AtomicInteger())
  }

  protected fun buildIndexForRoots(roots: Collection<VirtualFile>) {
    val hashing = FileContentHashing()
    val storage = createStorage(indexStorageFilePath)

    println("Writing indices to ${storage}")

    storage.use {
      val map = HashMap<HashCode, String>()
      for (file in roots) {
        println("Processing files in root ${file.path}")
        val stats = Stats()
        VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Boolean>() {
          override fun visitFile(file: VirtualFile): Boolean {
            return indexFile(file, hashing, map, storage, stats)
          }
        })
        println("${stats.indexed.get()} entries written, ${stats.skipped.get()} skipped")
      }
    }
  }

  private fun indexFile(file: VirtualFile,
                        hashing: FileContentHashing,
                        map: MutableMap<HashCode, String>,
                        storage: PersistentHashMap<HashCode, Value>,
                        stats: Stats): Boolean {
    try {
      if (fileFilter.accept(file)) {
        val fileContent = FileContentImpl.createByFile(file) as FileContentImpl

        val hashCode = hashing.hashString(fileContent)

        val value = getIndexValue(fileContent)

        if (value != null) {
          val item = map[hashCode]
          if (item == null) {
            storage.put(hashCode, value)

            stats.indexed.incrementAndGet()

            if (CHECK_HASH_COLLISIONS) {
              map[hashCode] = fileContent.contentAsText.toString()
            }
          }
          else {
            TestCase.assertEquals(item, fileContent.contentAsText.toString())
            TestCase.assertTrue(storage.get(hashCode) == value)
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