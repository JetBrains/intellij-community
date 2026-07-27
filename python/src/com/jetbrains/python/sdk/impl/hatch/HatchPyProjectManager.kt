// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl.hatch

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.hatch.cli.new
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.intellij.python.hatch.impl.HATCH_UI_INFO
import com.intellij.python.hatch.impl.sdk.HatchSdkFlavor
import com.intellij.python.hatch.runtime.createHatchRuntime
import com.intellij.python.hatch.runtime.hatchCli
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectCreator
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.impl.ToolBasedProjectCreator
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable
import kotlin.io.path.pathString

internal class HatchPyProjectManager : PyProjectManager, PyProjectCreator by ToolBasedProjectCreator(
  object : ToolBasedProjectCreator.PyToolFuns {
    override suspend fun createRuntime(
      fs: EelFileSystem,
      where: Directory,
    ): Result<PyToolRuntime, PyError> = createHatchRuntime(fs, null, where)

    override suspend fun createProject(
      name: @NlsSafe String?,
      runtime: PyToolRuntime,
      where: Directory,
    ): PyResult<*> {
      val projectName = name ?: where.fileName.pathString
      val initExistingProject = name == null
      return runtime.hatchCli<PathHolder.Eel>().new(projectName, initExistingProject = initExistingProject)
    }
  }
) {
  override val id: ToolId = HATCH_TOOL_ID
  override val ui: PyToolUIInfo = HATCH_UI_INFO

  override val flavorDataType: Class<HatchSdkFlavor> = HatchSdkFlavor::class.java

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo? = null

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = emptyList()
}
