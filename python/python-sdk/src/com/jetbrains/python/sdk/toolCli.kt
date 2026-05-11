// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.where
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.isExecutable

/**
 * Detects the path to a CLI tool executable in the given Eel environment, returns `null` it no tool found
 *
 * Search order (first match wins):
 * - [EelApi.exec.where] for the given [toolName].
 * - User-local locations depending on the target platform:
 *   - Unix-like: `~/.local/bin/<toolName>`
 *   - Windows: `%APPDATA%/Python/Scripts/<toolName>.exe`
 * - Provided [additionalSearchPaths], with each candidate resolved as `<path>/<toolName>` (or
 *   `<toolName>.exe|.bat` on Windows).
 *
 * Notes:
 * - Entries in [additionalSearchPaths] must belong to the same Eel descriptor as [eel];
 *   paths with a different descriptor are skipped and a warning is logged.
 *
 * @param toolName Name of the tool to locate (without an extension).
 * @param eel Eel environment to search in; defaults to the local Eel.
 * @param additionalSearchPaths Extra directories to probe, **must** be on the same descriptor as [eel].
 * @return [PyResult] containing the resolved executable [Path] on success; otherwise a localized error
 *   explaining that the executable could not be found on the target machine.
 */
// TODO: Should be dropped after migrating to FileSystem tool detection in Hatch
@ApiStatus.Internal
suspend fun detectTool(
  toolName: String,
  eel: EelApi = localEel,
  additionalSearchPaths: List<Path> = listOf(),
): Path? = withContext(Dispatchers.IO) {
  val binary = eel.exec.where(toolName)?.asNioPath()
  if (binary != null) {
    return@withContext binary
  }

  val binaryName = if (eel.platform.isWindows) "$toolName.exe" else toolName
  val paths = buildList {
    addCommonPaths(eel, binaryName)
    if (eel.platform.isWindows) addWindowsPaths(eel, binaryName)
    for (path in additionalSearchPaths) {
      assert(path.getEelDescriptor() == eel.descriptor) {
        "Additional search paths should be on the same descriptor as Eel API, but $path isn't on $eel"
      }
      add(path.resolve(binaryName))
    }
  }

  paths.firstOrNull { it.isExecutable() }
}

private fun MutableList<Path>.addCommonPaths(eel: EelApi, binaryName: String) {
  add(eel.userInfo.home.asNioPath().resolve(".local", "bin", binaryName))
}

private suspend fun MutableList<Path>.addWindowsPaths(eel: EelApi, binaryName: String) {
  val env = try {
    eel.exec.environmentVariables().eelIt().await()
  }
  catch (e: EelExecApi.EnvironmentVariablesException) {
    fileLogger().warn("Unable to get environment variables, won't check some known Windows locations", e)
    return
  }

  val envsToCheck = listOf("APPDATA", "LOCALAPPDATA")
  envsToCheck.forEach { envToCheck ->
    env[envToCheck]?.let {
      add(eel.fs.getPath(it).asNioPath().resolve(Path.of("Python", "Scripts", binaryName)))
    }
  }
}