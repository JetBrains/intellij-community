// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.normalizePackageName
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import org.toml.lang.psi.*

private val toolsWhiteList = listOf(
  "autoflake",
  "basedpyright",
  "black",
  "cibuildwheel",
  "cmake",
  "codespell",
  "comfy",
  "conan",
  "conda-lock",
  "coverage",
  "cython",
  "flake8",
  "flit",
  "flit-core",
  "hatch",
  "hatch-vcs",
  "hatchling",
  "isort",
  "make-env",
  "mypy",
  "ninja",
  "nitpick",
  "pdm",
  "poe",
  "poetry",
  "poetry-core",
  "pybind11",
  "pycln",
  "pydantic-mypy",
  "pylint",
  "pyright",
  "pytest",
  "pytoniq",
  "refurb",
  "ruff",
  "scikit-build",
  "sematic-release",
  "setuptools",
  "setuptools-rust",
  "setuptools-scm",
  "vulture",
  "wheel",
)

@Internal
@VisibleForTesting
class PyProjectTomlUsageCollector : ProjectUsagesCollector() {

  private val GROUP = EventLogGroup("python.toml.stats", 1)
  private val PYTHON_PYTOML_TOOLS = GROUP.registerEvent(
    "python.pyproject.tools",
    EventFields.String("name", toolsWhiteList),
  )

  // https://peps.python.org/pep-0518/
  private val PYTHON_BUILD_BACKEND = GROUP.registerEvent(
    "python.pyproject.buildsystem",
    EventFields.String("name", toolsWhiteList),
  )

  override fun getGroup(): EventLogGroup = GROUP

  override fun requiresReadAccess() = true

  override fun requiresSmartMode() = true

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val tools = mutableSetOf<String>()
    val buildSystems = mutableSetOf<String>()

    FileTypeIndex.processFiles(
      TomlFileType,
      { file: VirtualFile ->
        val psiFile = file.findPsiFile(project)
        if (file.name == PY_PROJECT_TOML && psiFile != null && psiFile.isValid) {
          collectTools(psiFile, tools)
          collectBuildBackends(psiFile, buildSystems)
        }

        return@processFiles true
      },
      ProjectScope.getContentScope(project))

    val metrics = mutableSetOf<MetricEvent>()
    tools.forEach { name ->
      metrics.add(PYTHON_PYTOML_TOOLS.metric(name))
    }

    buildSystems.forEach { name: String ->
      metrics.add(PYTHON_BUILD_BACKEND.metric(name))
    }

    return metrics
  }

  companion object {
    const val PY_PROJECT_TOML = "pyproject.toml"
    const val BUILD_SYSTEM = "build-system"
    const val BUILD_REQUIRES = "requires"
    const val TOOL_PREFIX = "tool."

    @JvmStatic
    fun collectTools(file: PsiFile, tools: MutableSet<String>) {
      val collected = file.children.map { element ->
        val key = (element as? TomlTable)?.header?.key?.text ?: ""
        val name = if (key.startsWith(TOOL_PREFIX))
          key.substringAfter(TOOL_PREFIX, "").substringBefore(".")
        else ""

        normalizePackageName(name)
      }.filter {
        it.isNotEmpty()
      }

      tools.addAll(collected)
    }

    @JvmStatic
    fun collectBuildBackends(file: PsiFile, systems: MutableSet<String>) {
      val collected = file.children
        .filter { element ->
          (element as? TomlTable)?.header?.key?.text == BUILD_SYSTEM
        }.flatMap { it ->
          it.children.filter { line ->
            val kv = (line as? TomlKeyValue)
            kv?.key?.text == BUILD_REQUIRES && kv.value as? TomlArray != null
          }.flatMap { line ->
            val array = (line as TomlKeyValue).value as TomlArray
            array.elements.mapNotNull {
              (it as? TomlLiteral)?.text
            }
          }
        }.mapNotNull {
          val requirement = PyRequirementParser.fromLine(normalizePackageName(it))
          requirement?.name
        }

      systems.addAll(collected)
    }
  }
}