// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.util.io.sha256Hex
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.incremental.ModuleBuildTarget
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * Remembers which compiler produced the Kotlin output of a target, and which module mapping it wrote.
 *
 * The output of another compiler, or of the legacy Kotlin JPS plugin, cannot be extended file by file. The builder
 * then compiles all Kotlin sources of the chunk once.
 *
 * The file has two lines: the compiler version, and the SHA-256 of `META-INF/<module>.kotlin_module` or [NO_MODULE_MAPPING].
 * A file with only the first line comes from an earlier version of the builder, and its module mapping is not checked.
 */
internal class TargetCompilerMarker(paths: BuildDataPaths, target: ModuleBuildTarget) {
  private val file: Path = paths.getTargetDataRootDir(target).resolve(FILE_NAME)

  private fun lines(): List<String> = if (Files.isRegularFile(file)) Files.readAllLines(file).map { it.trim() } else emptyList()

  fun read(): String? = lines().firstOrNull()?.ifEmpty { null }

  /** The time of the last successful compilation of the builder, or `null` when there is none. */
  fun lastWriteTime(): FileTime? = if (Files.isRegularFile(file)) Files.getLastModifiedTime(file) else null

  fun write(compilerVersion: String, moduleMapping: File) {
    Files.createDirectories(file.parent)
    Files.writeString(file, "$compilerVersion\n${digest(moduleMapping)}")
  }

  /**
   * @return `true` when [moduleMapping] differs from the module mapping that the builder wrote. Then another compiler
   * wrote the output after the last compilation of the builder.
   *
   * A missing file is no evidence: JPS deletes it with the outputs of the dirty sources.
   */
  fun isModuleMappingForeign(moduleMapping: File): Boolean {
    val recorded = lines().getOrNull(1)?.ifEmpty { null } ?: return false
    return moduleMapping.isFile && digest(moduleMapping) != recorded
  }

  private fun digest(moduleMapping: File): String = if (moduleMapping.isFile) sha256Hex(moduleMapping.toPath()) else NO_MODULE_MAPPING

  private companion object {
    const val FILE_NAME = "kotlin-jps-builder-compiler.txt"
    const val NO_MODULE_MAPPING = "-"
  }
}
