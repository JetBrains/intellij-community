// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl.hatch

import com.intellij.openapi.diagnostic.fileLogger
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
import com.intellij.python.pyproject.model.spi.resolveSrcRoots
import com.intellij.python.pyproject.safeGetArr
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
import java.nio.file.InvalidPathException
import java.nio.file.Path
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

  /**
   * A hatch build target names the package directory, so the directory that holds it is the source root (PY-88898):
   *
   * ```toml
   * [tool.hatch.build.targets.wheel]
   * packages = ["my_src/my_package"]  # -> my_src
   * ```
   *
   * A package in the project root has no parent directory, so a flat layout gives no source root.
   */
  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> {
    val packageDirs = PACKAGES_KEYS.flatMap { key ->
      toml.safeGetArr<String>(key, unquotedDottedKey = true).successOrNull.orEmpty()
    }
    return resolveSrcRoots(projectRoot, packageDirs.mapNotNull { it.parentDir() })
  }

  override suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo? = null

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = listOf(
    TomlDependencySpecification.GroupPep621Dependency("tool.hatch.envs", "dependencies"),
  )
}

/**
 * Every table where hatch accepts `packages`. `[tool.hatch.build]` applies to all targets,
 * and a target table overrides it for that target.
 */
private val PACKAGES_KEYS: List<String> = listOf(
  "tool.hatch.build.packages",
  "tool.hatch.build.targets.wheel.packages",
  "tool.hatch.build.targets.sdist.packages",
)

private val logger = fileLogger()

/** The directory that holds the package at [this] path, or `null` when the package sits in the project root. */
private fun String.parentDir(): String? =
  try {
    Path.of(this).parent?.toString()
  }
  catch (e: InvalidPathException) {
    logger.info("Not a path: '$this'", e)
    null
  }
