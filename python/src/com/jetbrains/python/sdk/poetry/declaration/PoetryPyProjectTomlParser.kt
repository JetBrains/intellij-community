// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry.declaration

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.python.pyproject.findTomlHeader
import com.intellij.python.pyproject.findTomlValueByKey
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.pyRequirement
import org.toml.lang.psi.*

internal object PoetryPyProjectTomlParser {
  fun getDependencies(project: Project, file: VirtualFile): List<PyRequirement> {
    val psiFile = PsiManager.getInstance(project).findFile(file) as? TomlFile ?: return emptyList()
    return declaredDependencies(psiFile)
  }


  fun declaredDependencies(file: TomlFile): List<PyRequirement> {
    val oldDependencies = parseOldFormatRequirements(file, PoetryConst.TOOLS_POETRY_DEPENDENCIES)
    val oldDevDependencies = parseOldFormatRequirements(file, PoetryConst.TOOLS_POETRY_DEV_DEPENDENCIES)
    val newDependencies = parseNewFormatRequirements(file)

    return oldDependencies + oldDevDependencies + newDependencies
  }

  private fun parseOldFormatRequirements(file: TomlFile, section: String): List<PyRequirement> {
    val tomlTable = file.findTomlHeader(section)
    val oldFormatDependenciesKeyValues = tomlTable?.children?.filterIsInstance<TomlKeyValue>() ?: emptyList()
    val requirements = oldFormatDependenciesKeyValues.map {
      val name = it.key.text
      val pyRequirement = when (val versionValue = it.value) {
        is TomlLiteral -> {
          val versionSpecs = PyRequirementParser.parseVersionSpecs(versionValue.text)
          pyRequirement(name, versionSpecs = versionSpecs, extras = emptyList())
        }
        is TomlInlineTable -> {
          val versionSpec = versionValue.findTomlValueByKey("version")?.text
          val versionSpecs = versionSpec?.let { PyRequirementParser.parseVersionSpecs(it) } ?: emptyList()
          val extrasRaw = versionValue.findTomlValueByKey("extras") as? TomlArray
          val extras = extrasRaw?.elements?.mapNotNull { element -> (element as? TomlLiteral)?.text } ?: emptyList()
          pyRequirement(name, versionSpecs, extras = extras)
        }
        else -> pyRequirement(name)
      }
      pyRequirement
    }
    return requirements
  }

  private fun parseNewFormatRequirements(file: TomlFile): List<PyRequirement> {
    val project = file.findTomlHeader(PoetryConst.PROJECT)
    val dependencies = project?.findTomlValueByKey(PoetryConst.DEPENDENCIES)
    if (dependencies !is TomlArray)
      return emptyList()

    val pyRequirements = dependencies.elements.mapNotNull { element ->
      if (element !is TomlLiteral)
        return@mapNotNull null

      val rawDep = element.text
      val clearedRaw = rawDep.replace('(', ' ').replace(')', ' ').replace(" ", "")
      PyRequirementParser.fromLine(clearedRaw)
    }
    return pyRequirements
  }
}