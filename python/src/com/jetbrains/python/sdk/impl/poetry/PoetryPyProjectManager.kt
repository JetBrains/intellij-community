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
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.poetry.PyPoetrySdkAdditionalData
import com.jetbrains.python.sdk.poetry.runPoetry
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable

internal class PoetryPyProjectManager : PyProjectManager {

  override val id: ToolId = POETRY_TOOL_ID
  override val ui: PyToolUIInfo = POETRY_UI_INFO

  override val additionalDataType: Class<PyPoetrySdkAdditionalData> = PyPoetrySdkAdditionalData::class.java

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
}
