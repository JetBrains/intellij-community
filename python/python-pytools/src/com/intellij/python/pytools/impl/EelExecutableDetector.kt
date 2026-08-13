// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.impl

import com.intellij.execution.Platform
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.where
import com.jetbrains.python.sdk.ToolCommandSpec
import com.jetbrains.python.sdk.ToolSearchPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.isExecutable

/**
 * Detects a tool executable on the machine [eelApi] describes: first on `PATH` (via `where`), then in
 * the [ToolCommandSpec.searchPaths] resolved against that machine's environment variables / home dir.
 * Returns the resolved nio [Path] (routed through the Eel filesystem provider for remote machines), or
 * `null` if not found.
 *
 * Internal detection primitive of `python-pytools`: the module's own resolution goes through
 * `PyExecutable.resolveExecutable` / `PyExecutableCache`; this is only exposed (as `@ApiStatus.Internal`)
 * because `EelFileSystem.detectTool` in `intellij.python.community.impl` delegates here.
 */
@ApiStatus.Internal
suspend fun detectExecutableOnEel(
  eelApi: EelApi,
  spec: ToolCommandSpec,
  filter: (Path) -> Boolean = { true },
): Path? = withContext(Dispatchers.IO) {
  val toolName = spec.toolName
  eelApi.exec.where(toolName)?.asNioPath()?.takeIf(filter)?.let { return@withContext it }

  val binaryNames = if (eelApi.platform.isWindows) listOf("$toolName.exe", "$toolName.bat") else listOf(toolName)
  for (dir in eelApi.resolveSearchDirs(spec)) {
    for (binaryName in binaryNames) {
      dir.resolve(binaryName).takeIf { it.isExecutable() }?.takeIf(filter)?.let { return@withContext it }
    }
  }
  null
}

private suspend fun EelApi.resolveSearchDirs(spec: ToolCommandSpec): List<Path> {
  val execPlatform = if (platform.isWindows) Platform.WINDOWS else Platform.UNIX
  return spec.searchPathsFor(execPlatform).mapNotNull { searchPath ->
    when (searchPath) {
      is ToolSearchPath.AbsolutePath -> parsePathOrNull(searchPath.path)
      is ToolSearchPath.RelativePath -> resolveFromEnv(searchPath.prefixEnvVar, searchPath.pathComponents)
      is ToolSearchPath.RelativePathFromHome -> searchPath.pathComponents.fold(userInfo.home.asNioPath(), Path::resolve)
    }
  }
}

private suspend fun EelApi.resolveFromEnv(prefixEnvVar: String, pathComponents: List<String>): Path? {
  val prefix = try {
    exec.environmentVariables().eelIt().await()[prefixEnvVar] ?: return null
  }
  catch (_: EelExecApi.EnvironmentVariablesException) {
    return null
  }
  return parsePathOrNull(prefix)?.let { pathComponents.fold(it, Path::resolve) }
}

private fun parsePathOrNull(raw: String): Path? = try {
  Path.of(raw)
}
catch (_: InvalidPathException) {
  null
}
