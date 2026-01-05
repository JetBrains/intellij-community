// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FUS_RECORDER
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

internal enum class PythonTool(val normalizedName: String, val markerFileNames: Set<String>) {
  AUTOFLAKE("autoflake"),
  AUTOIMPORT("autoimport"),
  BASEDPYRIGHT("basedpyright"),
  BANDIT("bandit", ".bandit"),
  BLACK("black", "black.toml"),
  CIBUILDWHEEL("cibuildwheel"),
  CHECK_JSONSCHEMA("check-jsonschema"),
  CODESPELL("codespell", ".codespellrc"),
  COMFY("comfy"),
  CONDA_LOCK("conda-lock"),
  COVERAGE("coverage", ".coveragerc"),
  CYTHON("cython"),
  DAGSTER("dagster"),
  DARKER("darker"),
  DEPTRY("deptry"),
  DOCFORMATTER("docformatter"),
  FLIT("flit", "flit.ini"),
  FLIT_CORE("flit-core"),
  FLAKE8("flake8", ".flake8"),
  GREAT_EXPECTATIONS("great-expectations", "great_expectations.yml"),
  HATCH("hatch", "hatch.toml", "hatch.lock"),
  HATCHLING("hatchling"),
  HATCH_VCS("hatch-vcs"),
  HYPOTHESIS("hypothesis", ".hypothesis"),
  ISORT("isort", ".isort.cfg"),
  MAKE_ENV("make-env"),
  MKDOCSTRINGS("mkdocstrings"),
  MYST_PARSER("myst-parser"),
  MYPY("mypy", "mypy.ini"),
  NBQA("nbqa"),
  NINJA("ninja"),
  NOX("nox", "noxfile.py"),
  PDOC("pdoc"),
  PDM("pdm", "pdm.lock"),
  PIXI("pixi", "pixi.toml", "pixi.lock"),
  POETRY("poetry", "poetry.lock"),
  POETRY_CORE("poetry-core"),
  POE("poe"),
  PREFECT("prefect", "prefect.yaml"),
  PYBIND11("pybind11"),
  PYCLN("pycln"),
  PYDANTIC_MYPY("pydantic-mypy"),
  PYRIGHT("pyright", "pyrightconfig.json"),
  PY_SPY("py-spy"),
  PYTKDOCS("pytkdocs"),
  PYTONIQ("pytoniq"),
  PYUPGRADE("pyupgrade"),
  REFURB("refurb"),
  RUFF("ruff", "ruff.toml"),
  SAFETY("safety"),
  SCITK_BUILD("scikit-build"),
  SCITK_BUILD_CORE("scikit-build-core"),
  SEMATIC_RELEASE("sematic-release"),
  SETUPTOOLS("setuptools", "setup.py", "setup.cfg"),
  SETUPTOOLS_RUST("setuptools-rust"),
  SETUPTOOLS_SCM("setuptools-scm"),
  SPHINX("sphinx"),
  TOX("tox", "tox.ini", "tox.toml"),
  UV("uv", "uv.lock"),
  VALIDATE_PYPROJECT("validate-pyproject"),
  VULTURE("vulture"),
  WHEEL("wheel"),
  YAPF("yapf", ".style.yapf");

  /**
   * For backward compatibility using normalized name instead of Enum name for FUS.
   */
  val fusName: String get() = normalizedName

  constructor(key: String, vararg markerFileNames: String) : this(key, markerFileNames.toSet())

  companion object {
    fun findByNormalizedName(normalizedName: String): PythonTool? = entries.find { it.normalizedName == normalizedName }
  }
}

internal val TRACKED_DEPENDENCY_GROUPS = listOf(
  "all", "async", "bench", "build", "ci",
  "cli", "coverage", "db", "debug", "deploy",
  "dev", "docs", "examples", "extras", "extras-all",
  "format", "gpu", "lint", "optional", "profile",
  "security", "test", "tooling", "typing", "viz"
)
internal const val DEPENDENCY_GROUP_OTHER = "other"

private val GROUP = EventLogGroup("python.toml.stats", 2, FUS_RECORDER, "Python Project Statistics")

internal val PYTHON_PYPROJECT_TOOLS = GROUP.registerEvent(
  "python.pyproject.tools",
  EventFields.Enum("name", PythonTool::class.java) { it.fusName },
  "A Python tool defined in the [tool.*] table of pyproject.toml"
)

// https://peps.python.org/pep-0518/
internal val PYTHON_PYPROJECT_BUILDSYSTEM = GROUP.registerEvent(
  "python.pyproject.buildsystem",
  EventFields.Enum("name", PythonTool::class.java) { it.fusName },
  "A Python tool defined in build-system.requires of pyproject.toml"
)

internal val PYTHON_TOOL_MARKERS_DETECTED = GROUP.registerEvent(
  "python.tool.markers.detected",
  EventFields.Enum("name", PythonTool::class.java) { it.fusName },
  "A Python tool detected via tool marker files (e.g., uv.lock, hatch.toml)"
)

internal val PYTHON_PYPROJECT_DEPENDENCY_GROUP = GROUP.registerEvent(
  "python.pyproject.dependency.group",
  EventFields.String("name", TRACKED_DEPENDENCY_GROUPS + DEPENDENCY_GROUP_OTHER),
  "A dependency group from pyproject.toml"
)

internal val PYTHON_PYPROJECT_COUNT = GROUP.registerEvent(
  "python.pyproject.count",
  EventFields.Int("count"),
  "Number of pyproject.toml files (python projects) in the workspace"
)

internal class PythonTomlStatsUsagesCollector : ProjectUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  override fun requiresReadAccess() = true

  override fun requiresSmartMode() = true

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val scope = ProjectScope.getContentScope(project)

    val pyProjectTomlCounter = AtomicInteger(0)
    val tools = mutableSetOf<PythonTool>()
    val buildSystems = mutableSetOf<PythonTool>()
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

    val toolsDetectedByMarkers = PythonTool.entries.filterNot { tool ->
      FilenameIndex.processFilesByNames(tool.markerFileNames, true, scope, null) { false }
    }

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
  fun findDeclaredTools(file: PsiFile): Set<PythonTool> {
    val declaredTools = file.children.mapNotNullTo(mutableSetOf()) { element ->
      val toolTomlKey = (element as? TomlTable)?.header?.key?.takeIf {
        it.segments.firstOrNull()?.text == PY_PROJECT_TOML_TOOL_PREFIX
      } ?: return@mapNotNullTo null

      val toolNormalizedName = toolTomlKey.segments.getOrNull(1)?.text?.let {
        PyPackageName.normalizePackageName(it)
      }

      toolNormalizedName?.let { PythonTool.findByNormalizedName(it) }
    }

    return declaredTools
  }

  fun findBuildSystemRequiresTools(file: PsiFile): Set<PythonTool> {
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
      requirement?.name?.let { key -> PythonTool.findByNormalizedName(key) }
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
