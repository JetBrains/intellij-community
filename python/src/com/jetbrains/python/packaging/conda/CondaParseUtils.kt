// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal object CondaParseUtils {
  fun parseOutdatedOutputs(jsonContent: String): List<PythonOutdatedPackage> {
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val condaOutput = json.decodeFromString<CondaOutput>(jsonContent)
    val prevVersions = condaOutput.actions.unlink.associateBy { it.name }
    val newVersions = condaOutput.actions.link.associateBy { it.name }

    val updatedPackage = prevVersions.keys.union(newVersions.keys)
    return updatedPackage.mapNotNull {
      val prevVersion = prevVersions[it]?.version ?: return@mapNotNull null
      val newVersion = newVersions[it]?.version ?: return@mapNotNull null
      PythonOutdatedPackage(it, prevVersion, newVersion)
    }
  }

  @Serializable
  private data class CondaOutput(val actions: CondaOutputActions = CondaOutputActions())

  @Serializable
  private data class CondaOutputActions(
    @SerialName("LINK")
    val link: List<CondaPackageInfo> = emptyList(),
    @SerialName("UNLINK")
    val unlink: List<CondaPackageInfo> = emptyList(),
  )

  @Serializable
  private data class CondaPackageInfo(val name: String, val version: String)
}