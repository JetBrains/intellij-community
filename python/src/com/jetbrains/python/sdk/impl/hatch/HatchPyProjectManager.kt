// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl.hatch

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.intellij.python.hatch.impl.HATCH_UI_INFO
import com.intellij.python.hatch.runtime.createHatchRuntime
import com.intellij.python.hatch.runtime.hatchCli
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectCreator
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.hatch.sdk.HatchSdkAdditionalData
import com.jetbrains.python.sdk.impl.ToolBasedProjectCreator
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable

internal class HatchPyProjectManager : PyProjectManager, PyProjectCreator by ToolBasedProjectCreator(
  createRuntime = { fs, dir -> createHatchRuntime(fs, null, dir) },
  createProject = { name, runtime -> runtime.hatchCli().new(name) }
) {

  override val id: ToolId = HATCH_TOOL_ID
  override val ui: PyToolUIInfo = HATCH_UI_INFO

  override val additionalDataType: Class<HatchSdkAdditionalData> = HatchSdkAdditionalData::class.java

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo? = null

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = emptyList()
}