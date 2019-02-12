// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index

import com.google.common.hash.HashCode
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.stubs.FileContentHashing
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.indexing.IndexingDataKeys
import java.io.Closeable
import java.io.IOException
import java.lang.RuntimeException
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
      if(exceptions.isNotEmpty()) throw exceptions[0]
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
        if (f.isDirectory) return@iterateFiles true
        val fileContent = FileContentImpl(f, f.contentsToByteArray())
        project?.let {  fileContent.putUserData(IndexingDataKeys.PROJECT, it) }
        val hash = hashing.hashString(fileContent)
        hashVerifier.checkContent(hash, fileContent)
        return@iterateFiles generatorWrapper.indexFile(f, fileContent, hash)
      }
    }

    println("${stats.indexed.get()} entries written, ${stats.skipped.get()} skipped")
  }

  protected abstract fun iterateFiles(fileVisitor: (VirtualFile) -> Boolean)

  protected open val project: Project? = null
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

class ProjectContentPrebuiltIndexer(override val project: Project,
                                    private val indicator: ProgressIndicator = ProgressManager.getInstance().progressIndicator): PrebuiltIndexer() {
  override fun iterateFiles(fileVisitor: (VirtualFile) -> Boolean) {
    FileBasedIndex.getInstance().iterateIndexableFiles({ f -> ReadAction.compute<Boolean, RuntimeException> { fileVisitor.invoke(f)} },
                                                       project,
                                                       indicator)
  }
}