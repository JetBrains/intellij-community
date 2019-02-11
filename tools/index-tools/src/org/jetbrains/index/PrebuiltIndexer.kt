// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index

import com.google.common.hash.HashCode
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.stubs.FileContentHashing
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.FileContentImpl
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

abstract class PrebuiltIndexer {
  companion object {
    const val CHECK_HASH_COLLISIONS_PROPERTY = "idea.index.generator.check.hash.collisions"
    val CHECK_HASH_COLLISIONS: Boolean = SystemProperties.`is`(CHECK_HASH_COLLISIONS_PROPERTY)
  }

  private class Generators(val generators: Array<IndexGenerator>, indexStorageFilePath: String): Closeable {
    val stats: Array<Stats>

    data class Stats(val indexed: AtomicInteger, val skipped: AtomicInteger) {
      constructor() : this(AtomicInteger(0), AtomicInteger(0))
    }

    init {
      try {
        for (generator in generators) {
          generator.openStorage(indexStorageFilePath)
        }
      }
      catch (e: IOException) {
        close()
        throw e
      }
      stats = generators.map { Stats() }.toTypedArray()
    }


    fun indexFile(file: VirtualFile, fileContent: FileContentImpl, hash: HashCode): Boolean {
      for ((i, generator) in generators.withIndex()) {
        if (generator.fileFilter.accept(file)) {
          when (generator.indexFile(file, fileContent, file.fileType, hash)) {
            FileIndexingResult.INDEXED -> stats[i].indexed.incrementAndGet()
            FileIndexingResult.SKIPPED -> stats[i].skipped.incrementAndGet()
            FileIndexingResult.EXCEPTION -> return false
          }
        }
      }
      return false
    }

    override fun close() {
      val exceptions = mutableListOf<IOException>()
      for (generator in generators) {
        try {
          generator.closeStorage()
        }
        catch (e: IOException) {
          exceptions.add(e)
        }
      }
      exceptions[0].let { throw it }
    }
  }


  fun buildIndex(generators: Array<IndexGenerator>,
                 indexStorageFilePath: String,
                 hashVerifier: ContentHashVerifier = if (CHECK_HASH_COLLISIONS) InMemoryContentHashVerifier() else ContentHashVerifier.DEAF) {
    val generatorWrapper = Generators(generators, indexStorageFilePath)
    val stats = Generators.Stats()
    val hashing = FileContentHashing()

    generatorWrapper.use {
      iterateFiles { f ->
        val fileContent = FileContentImpl(f, f.contentsToByteArray())
        val hash = hashing.hashString(fileContent)
        hashVerifier.checkContent(hash, fileContent)
        return@iterateFiles generatorWrapper.indexFile(f, fileContent, hash)
      }
    }

    println("${stats.indexed.get()} entries written, ${stats.skipped.get()} skipped")
  }

  abstract fun iterateFiles(fileVisitor: (VirtualFile) -> Boolean)
}

class RootsPrebuiltIndexer(private val roots: Collection<VirtualFile>): PrebuiltIndexer() {
  override fun iterateFiles(fileVisitor: (VirtualFile) -> Boolean) {
    for (root in roots) {
      VfsUtilCore.visitChildrenRecursively(root,
                                           object : VirtualFileVisitor<Boolean>() {
                                             override fun visitFile(file: VirtualFile): Boolean {
                                               return fileVisitor(file)
                                             }
                                           })
    }
  }
}