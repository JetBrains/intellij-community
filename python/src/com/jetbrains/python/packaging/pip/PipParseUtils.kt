// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal object PipParseUtils {
  private val log = thisLogger()
  private val json = Json { ignoreUnknownKeys = true }

  @JvmStatic
  fun parseListResult(jsonContent: String): List<PythonPackage> {
    val parsed = json.decodeFromStringLogError<List<PipPackageInfo>>(jsonContent)
    return parsed.map { PythonPackage(it.name, it.version, it.isEditable) }
  }

  fun parseOutdatedOutputs(jsonContent: String): List<PythonOutdatedPackage> {
    val parsed = json.decodeFromStringLogError<List<PipPackageInfo>>(jsonContent)
    return parsed.map { PythonOutdatedPackage(it.name, it.version, it.latestVersion) }
  }

  private inline fun <reified T> Json.decodeFromStringLogError(jsonContent: String): T {
    try {
      return decodeFromString<T>(jsonContent)
    }
    catch (e: SerializationException) {
      log.error("Bad JSON '$jsonContent'", e)
      throw e
    }
  }

  @Serializable
  private data class PipPackageInfo(
    @SerialName("name")
    val name: String = "",
    @SerialName("version")
    val version: String = "",
    @SerialName("latest_version")
    val latestVersion: String = "",
    @SerialName("editable_project_location")
    val editableProjectLocation: String = "",
  ) {
    val isEditable: Boolean = editableProjectLocation.isNotEmpty()
  }

}