// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.common.tools.ToolId
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.intellij.python.pyproject.model.internal.pyProjectToml.TomlDependencySpecification
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.Tool
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val HATCH_TOOL_ID: ToolId = ToolId("hatch")
val HATCH_UI_INFO: PyToolUIInfo = PyToolUIInfo("Hatch", PythonHatchIcons.Logo)

internal class HatchTool : Tool {

  override val id: ToolId = HATCH_TOOL_ID
  override val ui: PyToolUIInfo = HATCH_UI_INFO

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override suspend fun getProjectName(projectToml: TomlTable): @NlsSafe String? = null

  override suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo? = null

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = emptyList()
}
