// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry.backend


import com.intellij.openapi.util.NlsSafe
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.community.impl.poetry.common.POETRY_UI_INFO
import com.intellij.python.pyproject.model.internal.pyProjectToml.TomlDependencySpecification
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.Tool
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable

internal class PoetryTool : Tool {

  override val id: ToolId = POETRY_TOOL_ID
  override val ui: PyToolUIInfo = POETRY_UI_INFO

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override suspend fun getProjectName(projectToml: TomlTable): @NlsSafe String? =
    projectToml.getString("tool.poetry.name")

  override suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo? = null

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = listOf(
    TomlDependencySpecification.PathDependency("tool.poetry.dependencies"),
    TomlDependencySpecification.PathDependency("tool.poetry.dev-dependencies"),
    TomlDependencySpecification.GroupPathDependency("tool.poetry.group", "dependencies"),
  )
}
