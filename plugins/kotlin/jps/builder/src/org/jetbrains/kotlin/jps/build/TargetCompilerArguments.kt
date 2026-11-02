// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.kotlin.build.JvmBuildMetaInfo
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import java.nio.file.Files
import java.nio.file.Path

/**
 * Remembers the compiler arguments of the last successful compilation of a target.
 *
 * Some argument changes need a compilation of all Kotlin sources, see [JvmBuildMetaInfo.obtainReasonForRebuild]. The file
 * name and the format are the ones of the legacy Kotlin JPS plugin, so the builder reads the file that the plugin wrote.
 */
internal class TargetCompilerArguments(paths: BuildDataPaths, target: ModuleBuildTarget) {
  private val file: Path = paths.getTargetDataRootDir(target).resolve(FILE_NAME)
  private val metaInfo = JvmBuildMetaInfo()

  /** @return why [arguments] need a compilation of all Kotlin sources, or `null` when they do not. */
  fun reasonToRebuild(arguments: CommonCompilerArguments): String? {
    if (!Files.isRegularFile(file)) return null
    val previous = try {
      metaInfo.deserializeMapFromString(Files.readString(file))
    }
    catch (e: Exception) {
      KotlinBuilder.LOG.warn("Cannot read the previous compiler arguments from $file", e)
      return null
    }
    return metaInfo.obtainReasonForRebuild(metaInfo.createPropertiesMapFromCompilerArguments(arguments), previous)
  }

  fun write(arguments: CommonCompilerArguments) {
    Files.createDirectories(file.parent)
    Files.writeString(file, metaInfo.serializeArgsToString(arguments))
  }

  private companion object {
    const val FILE_NAME = "jvm-build-meta-info.txt"
  }
}
