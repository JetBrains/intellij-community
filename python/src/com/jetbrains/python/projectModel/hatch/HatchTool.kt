// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.hatch

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.Tool
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.ToolId
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val HATCH_TOOL_ID: ToolId = ToolId("hatch")

internal class HatchTool : Tool {

  override val id: ToolId = HATCH_TOOL_ID
  override val ui: PyToolUIInfo = PyToolUIInfo("Hatch", PythonSdkUIIcons.Tools.Hatch)

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override suspend fun getProjectName(projectToml: TomlTable): @NlsSafe String? = null

  override suspend fun getProjectStructure(entries: Map<ProjectName, PyProjectTomlProject>, rootIndex: Map<Directory, ProjectName>): ProjectStructureInfo? = null
}
