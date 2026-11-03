// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal val PIXI_SEARCH_OUTPUT_TRANSFORMER: ProcessOutputTransformer<String> = { output ->
  if (output.exitCode == 1 && output.stdoutString.isBlank() && output.stderrString.contains("No packages found matching")) {
    Result.success("{}")
  }
  else ZeroCodeStdoutTransformer(output)
}

internal data class PixiPackageSearchContext(val platform: String, val channels: List<String>, private val subdir: String = platform) {
  val arguments: List<String>
    get() = listOf("--platform=$platform") + channels.map { "--channel=$it" }

  val targetSelectors: Set<String>
    get() = setOf(platform, subdir) + when {
      subdir.startsWith("linux-") -> setOf("linux", "unix")
      subdir.startsWith("osx-") -> setOf("osx", "unix")
      subdir.startsWith("win-") -> setOf("win")
      else -> emptySet()
    }
}

internal fun parsePixiPackageSearchContext(output: String, environment: String): PyResult<PixiPackageSearchContext> {
  val info = try {
    INFO_JSON.decodeFromString<PixiInfo>(output)
  }
  catch (_: SerializationException) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.info.invalid.output"))
  }
  val selected = info.environments.firstOrNull { it.name == environment }
                 ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.environment.missing", environment))
  val platform = selected.resolvedPlatform ?: selected.platforms.filter { it.subdir == info.platform }.singleOrNull()
                 ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.platform.missing", environment))
  return PyResult.success(PixiPackageSearchContext(platform.name, selected.channels, platform.subdir))
}

private val INFO_JSON = Json { ignoreUnknownKeys = true }

@Serializable
private data class PixiInfo(
  val platform: String,
  @SerialName("environments_info") val environments: List<PixiEnvironmentInfo>,
)

@Serializable
private data class PixiEnvironmentInfo(
  val name: String,
  val channels: List<String>,
  val platforms: List<PixiPlatformInfo>,
  @SerialName("resolved_platform") val resolvedPlatform: PixiPlatformInfo? = null,
)

@Serializable
private data class PixiPlatformInfo(val name: String, val subdir: String)
