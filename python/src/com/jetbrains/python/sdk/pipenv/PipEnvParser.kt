// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.inspections.dependencies.DependenciesMap
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.psi.getStringOrNull
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.getValueByKey

private val dependencyHeaders = setOf("packages", "dev-packages")
private const val versionKey = "version"

internal object PipEnvParser {
  private val gson = Gson()

  @JvmStatic
  fun getPipFileLockRequirements(virtualFile: VirtualFile): List<PyRequirement>? {
    val pipFileLock = parsePipFileLock(virtualFile).getOrNull() ?: return null
    val packages = pipFileLock.packages?.let { toRequirements(it) } ?: emptyList()
    val devPackages = pipFileLock.devPackages?.let { toRequirements(it) } ?: emptyList()
    return packages + devPackages
  }

  @JvmStatic
  fun getPipFileDependenciesMap(file: TomlFile): DependenciesMap =
    file
      .children
      .filterIsInstance<TomlTable>()
      .filter { it.header.key?.text in dependencyHeaders }
      .flatMap {
        it.entries.mapNotNull { keyValue ->
          val versionString = when (val value = keyValue.value) {
            is TomlLiteral -> value.getStringOrNull()
            is TomlInlineTable ->
              value
                .getValueByKey(versionKey)
                ?.let { tomlValue -> tomlValue as? TomlLiteral }
                ?.getStringOrNull()
            else -> null
          }

          (PyRequirementParser.fromLine("${keyValue.key.text}${versionString ?: ""}")
           ?: PyRequirementParser.fromLine(keyValue.key.text))
            ?.let { pyRequirement -> pyRequirement to keyValue }
        }
      }
      .let { mapOf(*it.toTypedArray()) }
  

  @RequiresBackgroundThread
  private fun toRequirements(packages: Map<String, PipFileLockPackage>): List<PyRequirement> =
    packages.mapNotNull { (name, pkg) ->
      val packageVersion = "$name${pkg.version ?: ""}"
      PyRequirementParser.fromLine(packageVersion)
    }

  private fun parsePipFileLock(virtualFile: VirtualFile): Result<PipFileLock> {
    val text = runReadAction {
      FileDocumentManager.getInstance().getDocument(virtualFile)?.text
    }
    return try {
      Result.success(gson.fromJson(text, PipFileLock::class.java))
    }
    catch (e: JsonSyntaxException) {
      Result.failure(e)
    }
  }
}
