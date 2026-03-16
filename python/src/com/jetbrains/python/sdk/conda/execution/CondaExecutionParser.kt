// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda.execution

import com.intellij.openapi.util.IntellijInternalApi
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.sdk.conda.execution.models.CondaEnvInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(IntellijInternalApi::class)
internal object CondaExecutionParser {
  private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
  fun parseCondaPackageList(text: String): List<CondaPackage> {
    val parsed = json.parseToJsonElement(text).jsonArray
    return parsed.map {
      val jsonObject = it.jsonObject
      val name = jsonObject["name"]?.jsonPrimitive?.content ?: ""
      val version = jsonObject["version"]?.jsonPrimitive?.content ?: ""
      val channel = jsonObject["channel"]?.jsonPrimitive?.content ?: ""
      val isPypi = channel == "pypi"
      CondaPackage(name, version, editableMode = false, installedWithPip = isPypi)
    }
      .sortedWith(compareBy(CondaPackage::name))
      .toList()
  }


  fun parseListEnvironmentsOutput(jsonContent: String): CondaEnvInfo {
    return json.decodeFromString<CondaEnvInfo>(jsonContent)
  }

  fun parseOutdatedOutputs(jsonContent: String): List<PythonOutdatedPackage> {
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