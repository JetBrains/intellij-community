// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.pyproject.statistics.DEPENDENCY_GROUP_OTHER
import com.intellij.python.pyproject.statistics.PYTHON_PYPROJECT_BUILDSYSTEM
import com.intellij.python.pyproject.statistics.PYTHON_PYPROJECT_COUNT
import com.intellij.python.pyproject.statistics.PYTHON_PYPROJECT_DEPENDENCY_GROUP
import com.intellij.python.pyproject.statistics.PYTHON_PYPROJECT_TOOLS
import com.intellij.python.pyproject.statistics.PYTHON_TOOL_MARKERS
import com.intellij.python.pyproject.statistics.PYTHON_TOOL_MARKERS_DETECTED
import com.intellij.python.pyproject.statistics.PythonTomlStatsUsagesCollector
import com.intellij.python.pyproject.statistics.TRACKED_DEPENDENCY_GROUPS
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@PyDefaultTestApplication
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath($$"$CONTENT_ROOT/../python-pyproject/testData/statistics/tools")
class PythonTomlStatsUsagesCollectorTest(val project: Project) {
  @Test
  fun testMonorepo() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val metrics = PythonTomlStatsUsagesCollector().collect(project)
    val groups = metrics.groupBy({ it.eventId }, { it.data.build() })

    assertEquals(
      2,
      groups[PYTHON_PYPROJECT_COUNT.eventId]!!.single().values.first() as Int
    ) {
      "Number of pyproject.toml files does not match the test data"
    }

    // "viz" was selected as a non-declared group randomly to cover a not-found case
    val dependencyGroups = groups[PYTHON_PYPROJECT_DEPENDENCY_GROUP.eventId]!!.map { it.values.first() as String }
    assertEquals(
      (TRACKED_DEPENDENCY_GROUPS + DEPENDENCY_GROUP_OTHER - listOf("viz")).sorted(),
      dependencyGroups.sorted(),
    ) {
      "Dependency groups logged do not match the test data"
    }

    // ".bandit" was selected as a non-declared tool randomly to cover a not-found case
    val detectedToolsViaMarkers = groups[PYTHON_TOOL_MARKERS_DETECTED.eventId]!!.map { it.values.first() as String }
    assertEquals(
      (PYTHON_TOOL_MARKERS.keys - setOf("bandit")).sorted(),
      detectedToolsViaMarkers.sorted(),
    ) {
      "Detected tools via marker files do not match the test data"
    }

    val buildSystemTools = setOf(
      "setuptools", "wheel", "setuptools-scm", "setuptools-rust", "flit-core", "hatchling", "poetry-core", "scikit-build-core",
      "cython", "pybind11", "ninja", "sematic-release", "scikit-build"
    )

    val toolsFromBuildSystem = groups[PYTHON_PYPROJECT_BUILDSYSTEM.eventId]!!.map { it.values.first() as String }
    assertEquals(
      buildSystemTools.sorted(),
      toolsFromBuildSystem.sorted(),
    ) {
      "Detected tools via [build-system.requires] do not match the test data"
    }

    val sectionTools = listOf(
      "autoflake", "autoimport", "bandit", "basedpyright", "black",
      "check-jsonschema", "cibuildwheel", "codespell", "comfy", "conda-lock",
      "coverage", "dagster", "darker", "deptry", "docformatter",
      "flake8", "flit", "great-expectations", "hatch", "hatch-vcs",
      "hypothesis", "isort", "make-env", "mkdoc", "mkdocstrings",
      "mypy", "myst-parser", "nbqa", "nox", "pdm",
      "pdoc", "pixi", "poe", "poetry", "prefect",
      "py-spy", "pycln", "pydantic-mypy", "pyright", "pytkdocs",
      "pytoniq", "pyupgrade", "refurb", "ruff", "safety",
      "sphinx", "tox", "uv", "validate-pyproject", "vulture",
      "yapf"
    )
    val toolsFromPyProjectToml = groups[PYTHON_PYPROJECT_TOOLS.eventId]!!.map { it.values.first() as String }
    assertEquals(
      sectionTools.sorted(),
      toolsFromPyProjectToml.sorted(),
    ) {
      "Detected tools [tool.*] via pyproject.toml do not match the test data"
    }
  }
}