// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.workspace.DEFAULT_ENVIRONMENT
import com.intellij.python.pixi.backend.workspace.PixiTomlTable
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName

internal sealed interface PixiDependencyScope {
  val arguments: List<String>

  data object Default : PixiDependencyScope {
    override val arguments: List<String> = emptyList()
  }

  data class Feature(val name: String) : PixiDependencyScope {
    override val arguments: List<String> = listOf("--feature=$name")
  }

  data class Environment(val name: String) : PixiDependencyScope {
    override val arguments: List<String> = listOf("--environment=$name")
  }
}

internal enum class PixiDependencyStorage { TABLE, PYTHON_ARRAY, UNDECLARED }

internal data class PixiDependencyTarget(
  val name: String,
  val scope: PixiDependencyScope,
  val storage: PixiDependencyStorage = PixiDependencyStorage.TABLE,
)

internal fun resolvePixiDependencyTarget(
  manifest: PixiTomlTable,
  environment: String,
  name: String,
  pypi: Boolean,
  feature: String? = null,
  requirementName: (String) -> String?,
): PyResult<PixiDependencyTarget> {
  val pixi = manifest.getTable("tool.pixi") ?: manifest
  val environmentDefinition = pixi.getTable("environments")?.get(listOf(environment))
  if (environment != DEFAULT_ENVIRONMENT && environmentDefinition == null) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.environment.missing", environment))
  }
  val environmentTable = environmentDefinition as? PixiTomlTable
  val features = when (environmentDefinition) {
    is List<*> -> environmentDefinition.filterIsInstance<String>()
    is PixiTomlTable -> environmentDefinition.getArray("features").orEmpty().filterIsInstance<String>()
    else -> emptyList()
  }
  val declarations = mutableSetOf<PixiDependencyTarget>()
  var needsExplicitTarget = false
  fun matches(candidate: String): Boolean =
    if (pypi) PyPackageName.from(candidate).name == PyPackageName.from(name).name else candidate == name
  fun collect(table: PixiTomlTable?, scope: PixiDependencyScope) {
    if (table == null) return
    val sections = if (pypi) listOf("pypi-dependencies") else listOf("dependencies", "host-dependencies", "build-dependencies")
    for (section in sections) {
      table.getTable(section)?.keySet()?.filter(::matches)?.forEach {
        if (section == "host-dependencies" || section == "build-dependencies") needsExplicitTarget = true
        else declarations.add(PixiDependencyTarget(it, scope))
      }
    }
    val targets = table.getTable("target")
    for (platform in targets?.keySet().orEmpty()) {
      val target = targets?.getTable(listOf(platform)) ?: continue
      if (sections.any { section -> target.getTable(section)?.keySet().orEmpty().any(::matches) }) needsExplicitTarget = true
    }
  }
  fun collectRequirements(requirements: List<*>?, scope: PixiDependencyScope, visited: Set<String> = emptySet()) {
    if (requirements == null) return
    for (requirement in requirements) {
      when (requirement) {
        is String -> requirementName(requirement)?.takeIf(::matches)?.let {
          declarations.add(PixiDependencyTarget(it, scope, PixiDependencyStorage.PYTHON_ARRAY))
        }
        is PixiTomlTable -> {
          val group = requirement.getString("include-group") ?: continue
          if (group !in visited) {
            collectRequirements(manifest.getTable("dependency-groups")?.getArray(listOf(group)),
                                PixiDependencyScope.Feature(group), visited + group)
          }
        }
      }
    }
  }
  if (feature == "default" || feature == null && environmentTable?.getBoolean("no-default-feature") != true) {
    collect(pixi, PixiDependencyScope.Default)
    if (pypi && pixi !== manifest) collectRequirements(manifest.getArray("project.dependencies"), PixiDependencyScope.Default)
  }
  for (featureName in feature?.let { listOfNotNull(it.takeUnless { it == "default" }) } ?: features) {
    val scope = PixiDependencyScope.Feature(featureName)
    collect(pixi.getTable("feature")?.getTable(listOf(featureName)), scope)
    if (pypi && pixi !== manifest) {
      collectRequirements(manifest.getTable("dependency-groups")?.getArray(listOf(featureName)), scope, setOf(featureName))
      collectRequirements(manifest.getTable("project.optional-dependencies")?.getArray(listOf(featureName)), scope)
    }
  }
  if (feature == null) collect(environmentTable, PixiDependencyScope.Environment(environment))
  if (needsExplicitTarget || declarations.size > 1) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.dependency.ambiguous", name))
  }
  val fallback = when {
    feature == "default" -> PixiDependencyScope.Default
    feature != null -> PixiDependencyScope.Feature(feature)
    environment == DEFAULT_ENVIRONMENT && environmentTable?.getBoolean("no-default-feature") != true -> PixiDependencyScope.Default
    else -> PixiDependencyScope.Environment(environment)
  }
  return PyResult.success(declarations.singleOrNull() ?: PixiDependencyTarget(name, fallback, PixiDependencyStorage.UNDECLARED))
}
