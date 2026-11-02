// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget
import java.io.File

/**
 * The Kotlin files of one build round, dirty and removed, grouped by target.
 *
 * JPS decides what is dirty. The Kotlin builder never marks a file dirty itself.
 */
class KotlinDirtySourceFilesHolder(
  val chunk: ModuleChunk,
  val context: CompileContext,
  delegate: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
) {
  class TargetFiles(val target: ModuleBuildTarget, val removed: Collection<File>) {
    private val mutableDirty = LinkedHashMap<File, KotlinModuleBuildTarget.Source>()

    val dirty: Map<File, KotlinModuleBuildTarget.Source>
      get() = mutableDirty

    internal fun markDirty(file: File, root: JavaSourceRootDescriptor) {
      val isCrossCompiled = root is KotlinIncludedModuleSourceRoot
      val key = file.normalize().absoluteFile
      val old = mutableDirty.put(key, KotlinModuleBuildTarget.Source(file, isCrossCompiled))
      check(old == null || old.isCrossCompiled == isCrossCompiled) {
        "`$key` is already marked as dirty: old is cross compiled: ${old!!.isCrossCompiled}, new is cross compiled: $isCrossCompiled"
      }
    }
  }

  val byTarget: Map<ModuleBuildTarget, TargetFiles>

  init {
    val byTarget = LinkedHashMap<ModuleBuildTarget, TargetFiles>()
    for (target in chunk.targets) {
      val removedFiles = delegate.getRemoved(target).map { it.toFile() }.filter { it.isKotlinSourceFile }
      byTarget[target] = TargetFiles(target, removedFiles)
    }

    delegate.processDirtyFiles { target, file, root ->
      val targetFiles = byTarget[target] ?: error("processDirtyFiles must report only the targets of chunk `$chunk`, not `$target`")
      if (file.isKotlinSourceFile) {
        targetFiles.markDirty(file, root)
      }
      true
    }

    this.byTarget = byTarget
  }

  val hasDirtyOrRemovedFiles: Boolean
    get() = byTarget.values.any { it.dirty.isNotEmpty() || it.removed.isNotEmpty() }

  fun getDirtyFiles(target: ModuleBuildTarget): Map<File, KotlinModuleBuildTarget.Source> = byTarget[target]?.dirty ?: emptyMap()

  fun getRemovedFiles(target: ModuleBuildTarget): Collection<File> = byTarget[target]?.removed ?: emptyList()

  val allDirtyFiles: Set<File>
    get() = byTarget.values.flatMapTo(LinkedHashSet()) { it.dirty.keys }

  val allRemovedFiles: Set<File>
    get() = byTarget.values.flatMapTo(LinkedHashSet()) { it.removed }
}

val File.isKotlinSourceFile: Boolean
  get() = FileUtilRt.extensionEquals(name, "kt") || FileUtilRt.extensionEquals(name, "kts")
