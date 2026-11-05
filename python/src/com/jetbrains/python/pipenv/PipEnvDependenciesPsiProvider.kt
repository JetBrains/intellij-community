// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pipenv

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.python.community.impl.pipenv.PipEnvBundle
import com.intellij.python.requirements.parser.PyRequirementParser
import com.jetbrains.python.inspections.dependencies.DependenciesPsiProvider
import com.jetbrains.python.inspections.dependencies.DependencyMap
import com.jetbrains.python.psi.getStringOrNull
import com.jetbrains.python.sdk.pipenv.PIP_FILE
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.getValueByKey

private val dependencyHeaders = setOf("packages", "dev-packages")
private const val versionKey = "version"

internal class PipEnvDependenciesPsiProvider : DependenciesPsiProvider<TomlFile>(
  TomlFile::class.java,
  TomlLanguage,
) {
  override fun provideDependencies(file: TomlFile): DependencyMap? {
    if (file.name != PIP_FILE) {
      return null
    }

    return getPipFileDependenciesMap(file)
  }

  override val emptyFileInspectionMessage: @InspectionMessage String
    get() =
      PipEnvBundle.message("inspection.dependencies.pipenv.pipfile.empty")
}

internal fun getPipFileDependenciesMap(file: TomlFile): DependencyMap =
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
