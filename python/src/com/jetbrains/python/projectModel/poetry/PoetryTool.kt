// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.Tool
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.ToolId
import com.jetbrains.python.projectModel.common.getDependenciesFromToml
import com.jetbrains.python.projectModel.common.getProjectStructure
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val POETRY_TOOL_ID: ToolId = ToolId("poetry")
internal class PoetryTool : Tool {

  override val id: ToolId = POETRY_TOOL_ID
  override val ui: PyToolUIInfo = PyToolUIInfo("Poetry", PythonSdkUIIcons.Tools.Poetry)

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override suspend fun getProjectName(projectToml: TomlTable): @NlsSafe String? =
    projectToml.getString("tool.poetry.name")

  override suspend fun getProjectStructure(entries: Map<ProjectName, PyProjectTomlProject>, rootIndex: Map<Directory, ProjectName>): ProjectStructureInfo =
    getProjectStructure(entries, rootIndex) { getDependencies(it.root, it.pyProjectToml) }

  @RequiresBackgroundThread
  private fun getDependencies(rootDir: Directory, projectToml: PyProjectToml): Set<Directory> {
    val moduleDependenciesSet = getDependenciesFromToml(projectToml)
    val oldStyleModuleDependencies = projectToml.toml.getTableOrEmpty("tool.poetry.dependencies")
      .toMap().entries
      .mapNotNull { (_, depSpec) ->
        if (depSpec !is TomlTable || depSpec.getBoolean("develop") != true) return@mapNotNull null
        depSpec.getString("path")?.let { rootDir.resolve(it).normalize() }
      }
    return moduleDependenciesSet + oldStyleModuleDependencies.toSet()
  }
}
