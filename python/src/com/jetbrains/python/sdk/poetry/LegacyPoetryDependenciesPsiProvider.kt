// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.psi.PsiFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.inspections.dependencies.DependenciesPsiProvider
import com.jetbrains.python.inspections.dependencies.DependencyMap
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.psi.getStringOrNull
import com.jetbrains.python.requirements.getPythonSdk
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

private val poetryGroupRegex = Regex("""^tool\.poetry\.group\.[^.]*\.dependencies$""")
private val legacyPoetryDependencyHeaders = setOf("tool.poetry.dependencies", "tool.poetry.dev-dependencies")

internal class LegacyPoetryDependenciesPsiProvider : DependenciesPsiProvider<TomlFile>(
  TomlFile::class.java,
  TomlLanguage
) {
  override fun provideDependencies(file: TomlFile): DependencyMap? {
    if (file.name != PY_PROJECT_TOML || !isPoetryProject(file)) {
      return null
    }

    return file
      .children
      .filterIsInstance<TomlTable>()
      .filter {
        it.header.key?.text?.let { text ->
          text in legacyPoetryDependencyHeaders || poetryGroupRegex matches text
        } == true
      }
      .flatMap { it.children.filterIsInstance<TomlKeyValue>() }
      .mapNotNull { keyValue ->
        val versionString =
          (keyValue.value as? TomlLiteral)?.getStringOrNull()
          ?: return@mapNotNull null

        (PyRequirementParser.fromLine("${keyValue.key.text}${versionString}")
         ?: PyRequirementParser.fromLine(keyValue.key.text))
          ?.let { pyRequirement -> pyRequirement to keyValue }
      }
      .toMap()
  }

  override val emptyFileInspectionMessage: @InspectionMessage String? = null

  private fun isPoetryProject(psiFile: PsiFile) =
    getPythonSdk(psiFile)?.isPoetry == true
}
