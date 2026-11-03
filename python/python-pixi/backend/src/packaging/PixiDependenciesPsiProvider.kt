// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.workspace.DEFAULT_ENVIRONMENT
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.requirements.parser.PyRequirementParser
import com.jetbrains.python.inspections.dependencies.DependenciesPsiProvider
import com.jetbrains.python.inspections.dependencies.DependencyMap
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.management.PythonPackageManager
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlArray
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlArray as PsiTomlArray
import org.apache.tuweni.toml.TomlTable as ParsedTomlTable

internal class PixiDependenciesPsiProvider : DependenciesPsiProvider<TomlFile>(TomlFile::class.java, TomlLanguage) {
  override val emptyFileInspectionMessage: String? = null

  override fun provideDependencies(file: TomlFile): DependencyMap? {
    if (file.name !in setOf("pixi.toml", "pyproject.toml")) return null
    if (PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) return null
    val manifest = PixiInspectionManifest(file)
    val entries = manifest.entries
    val tables = manifest.tables
    val tablePaths = tables.mapNotNull(::keyPath)
    fun value(path: List<String>): Any? = manifest.value(path)
    val prefix = if (file.name == "pyproject.toml") listOf("tool", "pixi") else emptyList()
    if (prefix.isNotEmpty() && entries.keys.none { it.take(2) == prefix } && tablePaths.none { it.take(2) == prefix }) return null
    val interpreter = file.findEvoPyProjectIfReady(mainForOrphans = false)?.interpreter
    if (interpreter != null && interpreter.ref.manager != PixiPyTool.getInstance().fusId) return null
    val environment = interpreter?.ref?.envRef?.value ?: DEFAULT_ENVIRONMENT
    val environmentPath = prefix + listOf("environments", environment)
    val definition = value(environmentPath)
    if (environment != DEFAULT_ENVIRONMENT && definition == null && environmentPath !in tablePaths &&
        entries.keys.none { it.take(environmentPath.size) == environmentPath }) return emptyMap()
    val features = when (definition) {
      is TomlArray -> definition.toList().filterIsInstance<String>()
      else -> (value(environmentPath + "features") as? TomlArray)?.toList().orEmpty().filterIsInstance<String>()
    }
    val scopes = buildList {
      if (value(environmentPath + "no-default-feature") != true) add(prefix)
      features.forEach { add(prefix + listOf("feature", it)) }
      add(prefix + listOf("environments", environment))
    }
    val targetSelectors = interpreter?.let { PythonPackageManager.forPythonInterpreter(file.project, it) }
      ?.let { it as? PixiPackageManager }?.resolvedPlatformSelectors.orEmpty()
    val containers = scopes.flatMap { scope ->
      (listOf(scope) + targetSelectors.map { scope + listOf("target", it) }).flatMap {
        listOf(it + "dependencies", it + "pypi-dependencies")
      }
    }.toSet()
    val dependencies = linkedMapOf<PyRequirement, PsiElement>()
    fun collect(path: List<String>?, element: PsiElement) {
      if (path == null || path.dropLast(1) !in containers) return
      val specification = value(path)
      val version = if (path[path.lastIndex - 1] == "pypi-dependencies") when (specification) {
        is String -> specification
        is ParsedTomlTable -> specification.getString("version")
        else -> value(path + "version") as? String
      } else null
      val constraint = when {
        version == null || version == "*" -> ""
        version.firstOrNull() in listOf('<', '>', '=', '!', '~') -> version
        else -> "==$version"
      }
      PyRequirementParser.fromLine(path.last() + constraint, file.project)?.let { dependencies[it] = element }
    }
    for ((path, entry) in entries) collect(path, entry)
    for (table in tables) collect(keyPath(table), table.header)
    if (prefix.isNotEmpty()) {
      val activeGroups = features.toMutableSet()
      fun includeGroup(name: String) {
        val array = entries[listOf("dependency-groups", name)]?.value as? PsiTomlArray ?: return
        array.elements.mapNotNull { (parseValue(it) as? ParsedTomlTable)?.getString("include-group") }.forEach {
          if (activeGroups.add(it)) includeGroup(it)
        }
      }
      features.forEach(::includeGroup)
      for ((path, entry) in entries) {
        val active = when (path.dropLast(1)) {
          listOf("dependency-groups") -> path.last() in activeGroups
          listOf("project", "optional-dependencies") -> path.last() in features
          else -> false
        }
        if (!active) continue
        val elements = (entry.value as? PsiTomlArray)?.elements ?: continue
        for (element in elements) {
          val requirement = parseValue(element) as? String ?: continue
          PyRequirementParser.fromLine(requirement, file.project)?.let { dependencies[it] = element }
        }
      }
    }
    return dependencies
  }
}

private fun parseValue(element: PsiElement): Any? = Toml.parse("value = ${element.text}").takeUnless { it.hasErrors() }?.get("value")

internal class PixiInspectionManifest(file: TomlFile) {
  val entries: Map<List<String>, TomlKeyValue> = PsiTreeUtil.collectElementsOfType(file, TomlKeyValue::class.java).mapNotNull { entry ->
    keyPath(entry)?.let { it to entry }
  }.toMap()
  val tables: List<TomlTable> = file.children.filterIsInstance<TomlTable>()

  fun value(path: List<String>): Any? {
    entries[path]?.value?.let { return parseValue(it) }
    val parent = entries.keys.filter { path.take(it.size) == it }.maxByOrNull { it.size } ?: return null
    return (entries[parent]?.value?.let(::parseValue) as? ParsedTomlTable)?.get(path.drop(parent.size))
  }
}

internal fun keyPath(element: PsiElement): List<String>? {
  val path = mutableListOf<String>()
  var current: PsiElement? = element
  while (current != null) {
    when (current) {
      is PsiFile -> return path
      is TomlKeyValue -> path.addAll(0, current.key.segments.map { it.name ?: return null })
      is TomlHeaderOwner -> path.addAll(0, current.header.key?.segments?.map { it.name ?: return null } ?: return null)
    }
    current = current.parent
  }
  return null
}
