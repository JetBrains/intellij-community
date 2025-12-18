// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.jetbrains.python.PathShorter.Companion.create
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Replaces homepath with tilda and some well-known Windows vars as well.
 * 1. Create with [create]
 * 2. Convert paths *on the same eel* using [toString]
 */
@ApiStatus.Internal
class PathShorter private constructor(private val map: List<Pair<EelPath, String>>) {
  companion object {
    suspend fun create(project: Project): PathShorter = create(project.getEelDescriptor().toEelApi())
    suspend fun create(eelApi: EelApi): PathShorter {
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
      return PathShorter(map)
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
      result = result.replace(key.toString(), replaceWith)
    }
    return result
  }
}