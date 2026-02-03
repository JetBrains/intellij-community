// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda.environmentYml.format

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.parser.RequirementsParserHelper
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import kotlinx.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object CondaEnvironmentYmlParser {
  fun readNameFromFile(file: VirtualFile): String? = readFieldFromFile(file, "name")
  fun readPrefixFromFile(file: VirtualFile): String? = readFieldFromFile(file, "prefix")

  @RequiresBackgroundThread
  private fun readFieldFromFile(file: VirtualFile, field: String): String? = runReadAction {
    val text = FileDocumentManager.getInstance().getDocument(file)?.text ?: return@runReadAction null
    val mapper = ObjectMapper(YAMLFactory())
    val environment: JsonNode = mapper.readTree(text)

    environment.path(field).asText().takeIf { it.isNotEmpty() }
  }

  fun fromFile(file: VirtualFile): List<PyRequirement>? {
    val pyRequirements = try {
      readDeps(file)
    }
    catch (e: IOException) {
      thisLogger().info("Cannot parse deps from ${file.readText()}", e)
      return null
    }
    return pyRequirements.filter { it.name != "python" }.distinct()
  }

  @Throws(IOException::class)
  private fun readDeps(file: VirtualFile): List<PyRequirement> {
    val text = FileDocumentManager.getInstance().getDocument(file)?.text ?: return emptyList()
    val mapper = ObjectMapper(YAMLFactory())
    val environment: JsonNode = mapper.readTree(text)

    val result = mutableListOf<PyRequirement>()

    val dependencies = environment.path("dependencies")
    if (!dependencies.isArray) return emptyList()

    for (dependency in dependencies) {
      when {
        dependency.isTextual -> {
          val dep = dependency.asText()
          val parsed = parseCondaDep(dep) ?: continue
          result.add(parsed)
        }

        // Pip section (map with "pip" key)
        dependency.isObject -> {
          val pipList = dependency.path("pip")
          if (!pipList.isArray) continue

          val pipListDeps = parsePipListDeps(pipList, file)
          result.addAll(pipListDeps)
        }
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

  private fun parsePipListDeps(pipList: JsonNode, file: VirtualFile): List<PyRequirement> {
    val pipText = pipList.filter { it.isTextual }.joinToString("\n") { it.asText() }
    return PyRequirementParser.fromText(pipText, file, mutableSetOf<VirtualFile>())
  }
}
