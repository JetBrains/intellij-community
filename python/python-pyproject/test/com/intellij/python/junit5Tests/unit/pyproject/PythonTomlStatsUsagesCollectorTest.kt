// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.pyproject.statistics.*
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
      (PythonTool.entries.filter { it.markerFileNames.isNotEmpty() && it != PythonTool.BANDIT }).map { it.fusName }.sorted(),
      detectedToolsViaMarkers.sorted(),
    ) {
      "Detected tools via marker files do not match the test data"
    }

    // tools randomly distributed across [build-system.requires] and [tool.*]
    val buildSystemTools = setOf(
      PythonTool.SETUPTOOLS, PythonTool.WHEEL, PythonTool.SETUPTOOLS_SCM, PythonTool.SETUPTOOLS_RUST,
      PythonTool.FLIT_CORE, PythonTool.HATCHLING, PythonTool.POETRY_CORE, PythonTool.SCITK_BUILD_CORE,
      PythonTool.CYTHON, PythonTool.PYBIND11, PythonTool.NINJA, PythonTool.SEMATIC_RELEASE, PythonTool.SCITK_BUILD
    )

    val toolsFromBuildSystem = groups[PYTHON_PYPROJECT_BUILDSYSTEM.eventId]!!.map { it.values.first() as String }
    assertEquals(
      (PythonTool.entries.filter { it in buildSystemTools }).map { it.fusName }.sorted(),
      toolsFromBuildSystem.sorted(),
    ) {
      "Detected tools via [build-system.requires] do not match the test data"
    }

    val toolsFromPyProjectToml = groups[PYTHON_PYPROJECT_TOOLS.eventId]!!.map { it.values.first() as String }
    assertEquals(
      (PythonTool.entries.filter { it !in buildSystemTools }).map { it.fusName }.sorted(),
      toolsFromPyProjectToml.sorted(),
    ) {
      "Detected tools [tool.*] via pyproject.toml do not match the test data"
    }
  }
}