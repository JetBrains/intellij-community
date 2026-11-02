// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.incremental.ModuleBuildTarget
import java.nio.file.Files
import java.nio.file.Path

/**
 * Remembers which compiler produced the Kotlin output of a target.
 *
 * The output of another compiler, or of the legacy Kotlin JPS plugin, cannot be extended file by file. The builder
 * then asks JPS to rebuild the chunk once.
 */
internal class TargetCompilerMarker(paths: BuildDataPaths, target: ModuleBuildTarget) {
  private val file: Path = paths.getTargetDataRootDir(target).resolve(FILE_NAME)

  fun read(): String? = if (Files.isRegularFile(file)) Files.readString(file).trim().ifEmpty { null } else null

  fun write(compilerVersion: String) {
    Files.createDirectories(file.parent)
    Files.writeString(file, compilerVersion)
  }

  private companion object {
    const val FILE_NAME = "kotlin-jps-builder-compiler.txt"
  }
}
