// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.builders.storage.StorageProvider
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.storage.StorageOwner
import java.nio.file.Files
import java.nio.file.Path

/**
 * Keeps the module mapping (`META-INF/<module>.kotlin_module`) of the last successful compilation of a target.
 *
 * The mapping is an output of every source of the target, so JPS deletes it before the builder compiles a dirty or a
 * removed source. The compiler then needs the previous mapping to keep the parts of the sources that it does not compile
 * again. The Bazel builder gets these bytes from its output in the same way. JPS clears the storage on a rebuild.
 */
internal class KotlinModuleMappingStorage private constructor(private val file: Path) : StorageOwner {
  fun read(): ByteArray? = if (Files.isRegularFile(file)) Files.readAllBytes(file) else null

  fun write(content: ByteArray) {
    Files.createDirectories(file.parent)
    Files.write(file, content)
  }

  override fun clean() {
    Files.deleteIfExists(file)
  }

  override fun flush(memoryCachesOnly: Boolean) {
    // Nothing is buffered
  }

  override fun close() {
    // Nothing is open
  }

  companion object {
    private const val FILE_NAME = "kotlin-module-mapping.bin"

    private val PROVIDER = object : StorageProvider<KotlinModuleMappingStorage>() {
      override fun createStorage(targetDataDir: Path): KotlinModuleMappingStorage = KotlinModuleMappingStorage(targetDataDir.resolve(FILE_NAME))
    }

    fun of(context: CompileContext, target: ModuleBuildTarget): KotlinModuleMappingStorage =
      context.projectDescriptor.dataManager.getStorage(target, PROVIDER)
  }
}
