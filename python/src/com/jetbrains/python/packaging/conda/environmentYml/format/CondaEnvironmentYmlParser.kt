// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda.environmentYml.format

import com.charleskorn.kaml.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.parser.RequirementsParserHelper
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
object CondaEnvironmentYmlParser {
  fun fromFile(file: VirtualFile): List<PyRequirement>? {
    val pyRequirements = runCatching { readDeps(file) }.onFailure {
      thisLogger().info("Cannot parse deps from ${file.readText()}", it)
      return null
    }.getOrNull() ?: return null
    return pyRequirements.filter { it.name != "python" }.distinct()
  }

  private fun readDeps(file: VirtualFile): List<PyRequirement> {
    val text = FileDocumentManager.getInstance().getDocument(file)?.text ?: return emptyList()
    val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
    val environment: YamlMap = yaml.parseToYamlNode(text).yamlMap

    val result = mutableListOf<PyRequirement>()

    val dependencies = environment.get<YamlList>("dependencies") ?: return emptyList()
    for (dependency in dependencies.items) {
      when (dependency) {
        is YamlScalar -> {
          val dep = dependency.yamlScalar.content
          val parsed = parseCondaDep(dep) ?: continue
          result.add(parsed)
        }

        // Pip section (map with "pip" key)
        is YamlMap -> {
          val pipList = dependency.yamlMap.get<YamlList>("pip") ?: continue

          val pipListDeps = parsePipListDeps(pipList, file)
          result.addAll(pipListDeps)
        }
        else -> {}
      }
    }
    return result
  }

  private fun parseCondaDep(dep: String): PyRequirement? {
    // Skip URL-based, local file, git dependencies, and pip itself

    if (dep.startsWith("http") ||
        dep.startsWith("/") ||
        dep.startsWith("file:") ||
        RequirementsParserHelper.VCS_SCHEMES.any { dep.startsWith(it) } ||
        dep == "pip") {
      return null
    }

    // Handle channel-specific packages (strip channel prefix)
    val packageSpec = if (dep.contains("::")) {
      dep.substringAfter("::")
    }
    else {
      dep
    }

    val operations = PyRequirementRelation.entries.map { it.presentableText }
    // Check if the dependency already has version operators (>=, <=, >, <, !=)
    if (operations.any { dep.contains(it) }) {
      return PyRequirementParser.fromLine(packageSpec)
    }

    // Handle complex version constraints with commas
    if (packageSpec.contains(",")) {
      return PyRequirementParser.fromLine(packageSpec)
    }

    // Convert conda version format to pip format
    // Handle build strings and build numbers (package=version=build -> package==version)
    val parts = packageSpec.split("=")
    val packageName = parts[0]

    if (parts.size == 1) {
      // No version specified
      return PyRequirementParser.fromLine(packageName)
    }

    // Handle version specification
    val version = parts[1]

    // Ignore build strings (third part after =)
    // Convert = to == for exact version match
    return PyRequirementParser.fromLine("$packageName==$version")
  }

  private fun parsePipListDeps(pipList: YamlList, file: VirtualFile): List<PyRequirement> {
    val pipText = pipList.items.filterIsInstance<YamlScalar>().joinToString("\n") { it.yamlScalar.content }
    return PyRequirementParser.fromText(pipText, file, mutableSetOf<VirtualFile>())
  }
}
