// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl.poetry


import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.community.impl.poetry.common.POETRY_UI_INFO
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.PySdkDependencyGroupSupport
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.poetry.PoetryDependencyGroupSupport
import com.jetbrains.python.sdk.poetry.PyPoetrySdkAdditionalData
import com.jetbrains.python.sdk.poetry.runPoetry
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable

internal class PoetryPyProjectManager : PyProjectManager {

  override val id: ToolId = POETRY_TOOL_ID
  override val ui: PyToolUIInfo = POETRY_UI_INFO

  override val additionalDataType: Class<PyPoetrySdkAdditionalData> = PyPoetrySdkAdditionalData::class.java

  override val dependencyGroupSupport: PySdkDependencyGroupSupport = PoetryDependencyGroupSupport

  override suspend fun createProject(
    where: Directory,
    name: @NlsSafe String?,
  ): PyResult<Unit> {
    val args = if (name != null) arrayOf("new", name) else arrayOf("init")
    return runPoetry(where, *args, "-n").mapSuccess { }
  }

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo? = null

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = listOf(
    TomlDependencySpecification.PathDependency("tool.poetry.dependencies"),
    TomlDependencySpecification.PathDependency("tool.poetry.dev-dependencies"),
    TomlDependencySpecification.GroupPathDependency("tool.poetry.group", "dependencies"),
  )

  // Poetry-specific dependency-group shapes. PEP 621 / PEP 735 shapes are covered by the shared
  // spec dispatcher and don't need to be handled here.
  override fun resolveHeaderPath(path: List<String>): String? {
    val poetryDependencyPaths = listOf(TOOL, POETRY, DEPENDENCIES)
    return when {
      // [tool.poetry.dependencies] → the implicit "main" group.
      path == poetryDependencyPaths -> MAIN
      // [tool.poetry.group.<name>.dependencies] → the "<name>" group. Fixed length + prefix/suffix
      // so unrelated tables (e.g. [tool.poetry.dev-dependencies]) don't leak through.
      path.size == 5 && path[0] == TOOL && path[1] == POETRY && path[2] == GROUP && path[4] == DEPENDENCIES -> path[3]
      else -> null
    }
  }

  companion object {
    private const val MAIN = "main"
    private const val TOOL = "tool"
    private const val POETRY = "poetry"
    private const val GROUP = "group"
    private const val DEPENDENCIES = "dependencies"
  }
}
