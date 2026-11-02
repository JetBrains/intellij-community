// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.kotlin.config.SettingConstants
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * The incremental caches of the legacy Kotlin JPS plugin in the JPS data of a project.
 *
 * The IDE can switch a project from this builder back to the legacy plugin: a registry key, or a Kotlin version below
 * the version that the builder needs. The legacy plugin does not know about the output of this builder:
 * - For a target without `has-kotlin-marker.txt`, the legacy plugin compiles all Kotlin sources of the chunk itself.
 * - For a target with the marker, it builds incrementally against its own caches, which are stale.
 *
 * So, before this builder compiles a target, it hands the target over, see [handOver]. When the target has legacy
 * caches, the builder deletes them and keeps the marker. The legacy plugin then sees a missing cache version and
 * compiles all Kotlin sources once (`CacheAttributesDiff.status == INVALID` in `KotlinCompileContext.checkCacheVersions`).
 *
 * The same files tell this builder that the legacy plugin compiled a target after it, see [compiledSince].
 */
internal object LegacyKotlinJpsState {
  /** `KOTLIN_CACHE_DIRECTORY_NAME` of the legacy plugin: the caches of a target, in the data directory of the target. */
  private const val CACHE_DIRECTORY_NAME = "kotlin"

  /** The legacy plugin rewrites this file after each successful compilation of a target, with or without the caches. */
  private const val HAS_KOTLIN_MARKER_FILE_NAME = "has-kotlin-marker.txt"

  /** The version of the caches of a target. The legacy plugin writes it when the incremental compilation is on. */
  private const val CACHE_VERSION_FILE_NAME = "format-version.txt"

  /**
   * @return whether the legacy plugin compiled [target] after [lastCompilation], the last successful compilation of
   * this builder.
   */
  fun compiledSince(paths: BuildDataPaths, target: ModuleBuildTarget, lastCompilation: FileTime): Boolean {
    val dataRoot = paths.getTargetDataRootDir(target)
    if (Files.exists(dataRoot.resolve(CACHE_VERSION_FILE_NAME))) return true
    val hasKotlinMarker = dataRoot.resolve(CACHE_DIRECTORY_NAME).resolve(HAS_KOTLIN_MARKER_FILE_NAME)
    return Files.isRegularFile(hasKotlinMarker) && Files.getLastModifiedTime(hasKotlinMarker) > lastCompilation
  }

  /** @return whether the legacy plugin compiled [target] at some time. Use it for a target that this builder never compiled. */
  fun hasCompiled(paths: BuildDataPaths, target: ModuleBuildTarget): Boolean {
    val dataRoot = paths.getTargetDataRootDir(target)
    return Files.exists(dataRoot.resolve(CACHE_VERSION_FILE_NAME)) ||
           Files.isRegularFile(dataRoot.resolve(CACHE_DIRECTORY_NAME).resolve(HAS_KOTLIN_MARKER_FILE_NAME))
  }

  /**
   * Deletes the legacy caches of [target], so that the legacy plugin compiles all Kotlin sources of [target] next time.
   *
   * A target without legacy caches gets no marker: the legacy plugin then compiles the chunk in full by itself, and
   * this builder leaves no `kotlin` directory in the data of a project that never used the legacy plugin.
   */
  fun handOver(paths: BuildDataPaths, target: ModuleBuildTarget) {
    val dataRoot = paths.getTargetDataRootDir(target)
    val cacheDirectory = dataRoot.resolve(CACHE_DIRECTORY_NAME)
    val hasKotlinMarker = cacheDirectory.resolve(HAS_KOTLIN_MARKER_FILE_NAME)

    var hadCaches = Files.deleteIfExists(dataRoot.resolve(CACHE_VERSION_FILE_NAME))
    if (Files.isDirectory(cacheDirectory)) {
      Files.list(cacheDirectory).use { entries ->
        for (entry in entries) {
          if (entry == hasKotlinMarker) continue
          NioFiles.deleteRecursively(entry)
          hadCaches = true
        }
      }
    }
    if (!hadCaches) return

    // The legacy plugin checks the cache versions only for a target with this marker. An existing marker is not
    // rewritten, because a time later than the last compilation of this builder means a compilation of the legacy plugin.
    if (!Files.isRegularFile(hasKotlinMarker) || Files.readString(hasKotlinMarker).trim() != true.toString()) {
      Files.createDirectories(cacheDirectory)
      Files.writeString(hasKotlinMarker, true.toString())
    }
  }

  /**
   * Deletes the lookup storage of the legacy plugin, with its version file. It is common to all targets, so a missing
   * version makes the legacy plugin compile all Kotlin sources of the project.
   *
   * The deletion is best effort. The Kotlin compiler reference index of the IDE can hold the storage open, and Windows
   * does not delete an open file. The handed over targets still lose their cache versions, see [handOver].
   */
  fun deleteLookups(paths: BuildDataPaths) {
    val lookups: Path = paths.targetsDataRoot.resolve(SettingConstants.KOTLIN_DATA_CONTAINER_ID)
    if (!Files.exists(lookups)) return
    try {
      NioFiles.deleteRecursively(lookups)
    }
    catch (e: IOException) {
      KotlinBuilder.LOG.warn("Cannot delete the lookup storage of the legacy Kotlin JPS plugin at $lookups", e)
    }
  }
}
