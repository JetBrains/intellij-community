// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.jetbrains.python.PathShortener.Companion.create
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Replaces homepath with tilda and some well-known Windows vars as well.
 * 1. Create with [create]
 * 2. Convert paths *on the same eel* using [toString]
 */
@ApiStatus.Internal
class PathShortener private constructor(private val map: List<Pair<EelPath, String>>, private val ignoreCase: Boolean) {
  companion object {
    suspend fun create(project: Project): PathShortener = create(project.getEelDescriptor().toEelApi())

    /**
     * Reading env variables takes time. For multiple calls, prefer [create] and reuse.
     */
    suspend fun shorten(path: Path): @NlsSafe String = create(path.getEelDescriptor().toEelApi()).toString(path)
    suspend fun create(eelApi: EelApi): PathShortener {
      val eelDescriptor = eelApi.descriptor
      val map = buildList {
        if (eelDescriptor.osFamily.isWindows) {
          val envs = try {
            eelApi.exec.environmentVariables().eelIt().await()
          }
          catch (e: EelExecApi.EnvironmentVariablesException) {
            logger.warn("Can't fetch vars for $eelDescriptor", e)
            emptyMap()
          }
          for (winVar in arrayOf("APPDATA", "LOCALAPPDATA")) {
            envs[winVar]?.let { value ->
              add(Pair(EelPath.parse(value, eelDescriptor), winVar))
            }
          }
        }
        // Valid both for Windows and **nix
        add(Pair(eelApi.fs.user.home, "~"))
      }
      val ignoreCase = when (eelApi.platform) {
        is EelPlatform.Darwin -> true // Despite SUS, OS X is case-insensitive
        is EelPlatform.Windows -> true
        is EelPlatform.Posix -> false
      }
      return PathShortener(map, ignoreCase = ignoreCase)
    }

    private val logger = fileLogger()
  }

  /**
   * Convert [path] to user-readable string replacing home with tild and so on
   */
  fun toString(path: Path): @NlsSafe String {
    val pathDescriptor = path.getEelDescriptor()
    var result = path.asEelPath().toString()
    for ((key, replaceWith) in map) {
      assert(pathDescriptor == key.descriptor) { "path is on $pathDescriptor, replacer is on ${key.descriptor}" }
      result = result.replace(key.toString(), replaceWith, ignoreCase = ignoreCase)
    }
    return result
  }
}