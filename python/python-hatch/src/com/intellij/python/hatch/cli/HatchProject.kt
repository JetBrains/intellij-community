// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.hatch.runtime.HatchRuntime
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class Readme(
  @SerialName("content-type")
  val contentType: String,
  val text: String,
)

/**
 * format of hatchling.metadata.spec
 */
@Serializable
data class Metadata(
  val name: String,

  val version: String,

  val readme: Readme? = null,

  val license: JsonElement? = null,

  @SerialName("license-files")
  val licenseFiles: JsonElement? = null,

  val description: String? = null,

  val keywords: List<String>? = null,

  val classifiers: List<String>? = null,

  val urls: Map<String, String>? = null,

  val authors: JsonElement? = null,

  val maintainers: JsonElement? = null,

  @SerialName("requires-python")
  val requiresPython: String? = null,

  val dependencies: JsonElement? = null,

  @SerialName("optional-dependencies")
  val optionalDependencies: JsonElement? = null,
)

/**
 * Manage environment dependencies
 */
class HatchProject(runtime: HatchRuntime) : HatchCommand("project", runtime) {

  /**
   * Display project metadata
   */
  suspend fun metadata(): Result<Metadata, ExecError> {
    return executeAndHandleErrors("metadata") { processOutput ->
      val output = processOutput.takeIf { it.exitCode == 0 }?.stdoutString
                   ?: return@executeAndHandleErrors Result.failure(null)

      val json = Json { ignoreUnknownKeys = true }
      try {
        val metadata = json.decodeFromString<Metadata>(output)
        Result.success(metadata)
      }
      catch (e: Exception) {
        Result.failure(e.localizedMessage)
      }
    }
  }
}