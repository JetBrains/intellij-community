// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PY_PROJECT_TOML_BUILD_SYSTEM
import com.intellij.python.pyproject.PY_PROJECT_TOML_DEPENDENCY_GROUPS
import com.intellij.python.pyproject.PY_PROJECT_TOML_TOOL_PREFIX
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirementParser
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import java.util.concurrent.atomic.AtomicInteger

internal val PYTHON_TOOL_MARKERS: Map<String, Set<String>> = mapOf(
  "bandit" to setOf(".bandit"),
  "black" to setOf("black.toml"),
  "codespell" to setOf(".codespellrc"),
  "coverage" to setOf(".coveragerc"),
  "flit" to setOf("flit.ini"),
  "flake8" to setOf(".flake8"),
  "great-expectations" to setOf("great_expectations.yml"),
  "hatch" to setOf("hatch.toml", "hatch.lock"),
  "hypothesis" to setOf(".hypothesis"),
  "isort" to setOf(".isort.cfg"),
  "mypy" to setOf("mypy.ini", ".mypy.ini"),
  "nox" to setOf("noxfile.py"),
  "pdm" to setOf("pdm.lock"),
  "pixi" to setOf("pixi.toml", "pixi.lock"),
  "poetry" to setOf("poetry.lock"),
  "prefect" to setOf("prefect.yaml"),
  "pyright" to setOf("pyrightconfig.json"),
  "pytest" to setOf("pytest.toml", ".pytest.toml", "pytest.ini", ".pytest.ini"),
  "ruff" to setOf("ruff.toml", ".ruff.toml"),
  "setuptools" to setOf("setup.py", "setup.cfg"),
  "tox" to setOf("tox.ini", "tox.toml"),
  "uv" to setOf("uv.lock"),
  "yapf" to setOf(".style.yapf")
)

internal val TRACKED_DEPENDENCY_GROUPS = listOf(
  "all", "async", "bench", "build", "ci",
  "cli", "coverage", "db", "debug", "deploy",
  "dev", "docs", "examples", "extras", "extras-all",
  "format", "gpu", "lint", "optional", "profile",
  "security", "test", "tooling", "typing", "viz"
)
internal const val DEPENDENCY_GROUP_OTHER = "other"

private val GROUP = EventLogGroup("python.toml.stats", 3)
private val PACKAGE_NAME_FIELD = EventFields.StringValidatedByDictionary("name", "python_packages.ndjson")

internal val PYTHON_PYPROJECT_TOOLS = GROUP.registerEvent("python.pyproject.tools", PACKAGE_NAME_FIELD)

// https://peps.python.org/pep-0518/
internal val PYTHON_PYPROJECT_BUILDSYSTEM = GROUP.registerEvent("python.pyproject.buildsystem", PACKAGE_NAME_FIELD)

internal val PYTHON_TOOL_MARKERS_DETECTED = GROUP.registerEvent("python.tool.markers.detected", PACKAGE_NAME_FIELD)

internal val PYTHON_PYPROJECT_DEPENDENCY_GROUP = GROUP.registerEvent(
  "python.pyproject.dependency.group",
  EventFields.String("name", TRACKED_DEPENDENCY_GROUPS + DEPENDENCY_GROUP_OTHER)
)

internal val PYTHON_PYPROJECT_COUNT = GROUP.registerEvent("python.pyproject.count", EventFields.Int("count"))

internal class PythonTomlStatsUsagesCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun requiresReadAccess() = true

  override fun requiresSmartMode() = true

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val scope = ProjectScope.getContentScope(project)

    val pyProjectTomlCounter = AtomicInteger(0)
    val tools = mutableSetOf<String>()
    val buildSystems = mutableSetOf<String>()
    val dependencyGroups = mutableSetOf<String>()

    FilenameIndex.processFilesByName(PY_PROJECT_TOML, true, scope) { virtualFile ->
      virtualFile.findPsiFile(project)?.takeIf { it.isValid }?.let { psiFile ->
        pyProjectTomlCounter.incrementAndGet()
        tools.addAll(PyProjectTomlCollector.findDeclaredTools(psiFile))
        buildSystems.addAll(PyProjectTomlCollector.findBuildSystemRequiresTools(psiFile))
        dependencyGroups.addAll(PyProjectTomlCollector.findDependencyGroups(psiFile))
      }
      true
    }

    val toolsDetectedByMarkers = PYTHON_TOOL_MARKERS.entries.filterNot { (_, markerFileNames)  ->
      FilenameIndex.processFilesByNames(markerFileNames, true, scope, null) { false }
    }.map { it.key }

    val metrics = mutableSetOf<MetricEvent>()

    metrics.add(PYTHON_PYPROJECT_COUNT.metric(pyProjectTomlCounter.get()))
    tools.mapTo(metrics) { PYTHON_PYPROJECT_TOOLS.metric(it) }
    buildSystems.mapTo(metrics) { PYTHON_PYPROJECT_BUILDSYSTEM.metric(it) }
    dependencyGroups.mapTo(metrics) { PYTHON_PYPROJECT_DEPENDENCY_GROUP.metric(it) }
    toolsDetectedByMarkers.mapTo(metrics) { PYTHON_TOOL_MARKERS_DETECTED.metric(it) }

    return metrics
  }
}

internal object PyProjectTomlCollector {
  fun findDeclaredTools(file: PsiFile): Set<String> {
    val declaredTools = file.children.mapNotNullTo(mutableSetOf()) { element ->
      val toolTomlKey = (element as? TomlTable)?.header?.key?.takeIf {
        it.segments.firstOrNull()?.text == PY_PROJECT_TOML_TOOL_PREFIX
      } ?: return@mapNotNullTo null

      val toolNormalizedName = toolTomlKey.segments.getOrNull(1)?.text?.let {
        PyPackageName.normalizePackageName(it)
      }

      toolNormalizedName
    }

    return declaredTools
  }

  fun findBuildSystemRequiresTools(file: PsiFile): Set<String> {
    val buildSystemTables = file.children.mapNotNull { psiElement ->
      (psiElement as? TomlTable)?.takeIf { it.header.key?.text == PY_PROJECT_TOML_BUILD_SYSTEM }
    }

    val requiresValues = buildSystemTables.flatMap { tomlTable ->
      tomlTable.children.mapNotNull { line ->
        (line as? TomlKeyValue)?.takeIf { kv -> kv.key.text == "requires" }?.value
      }
    }

    val literals = requiresValues.flatMap { tomlValue ->
      (tomlValue as? TomlArray)?.elements?.mapNotNull { (it as? TomlLiteral)?.text } ?: emptyList()
    }

    val buildTools = literals.mapNotNullTo(mutableSetOf()) {
      val requirement = PyRequirementParser.fromLine(it.removeSurrounding("\""))
      requirement?.name
    }

    return buildTools
  }

  fun findDependencyGroups(file: PsiFile): List<String> {
    val dependencyGroupTables = file.children.mapNotNull { psiElement ->
      (psiElement as? TomlTable)?.takeIf { it.header.key?.text == PY_PROJECT_TOML_DEPENDENCY_GROUPS }
    }
    val dependencyGroups = dependencyGroupTables.flatMap { tomlTable ->
      tomlTable.children.mapNotNull { (it as? TomlKeyValue)?.key?.text }
    }.distinct().map { it.takeIf { it in TRACKED_DEPENDENCY_GROUPS } ?: DEPENDENCY_GROUP_OTHER }.sorted()

    return dependencyGroups
  }
}
