// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.*
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.ToolId
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.toPath

@ApiStatus.Internal
val POETRY_TOOL_ID: ToolId = ToolId("poetry")
internal class PoetryTool : Tool {

  override val id: ToolId = POETRY_TOOL_ID
  override val ui: PyToolUIInfo = PyToolUIInfo("Poetry", PythonSdkUIIcons.Tools.Poetry)

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override suspend fun getProjectName(projectToml: TomlTable): @NlsSafe String? =
    projectToml.getString("tool.poetry.name")

  override suspend fun getProjectStructure(entries: Map<ProjectName, PyProjectTomlProject>, rootIndex: Map<Directory, ProjectName>): ProjectStructureInfo = withContext(Dispatchers.Default) {
    val deps = entries.asSequence().map { (name, entry) ->
      val deps = getDependencies(entry.root, entry.pyProjectToml).mapNotNull { dir ->
        rootIndex[dir] ?: run {
          logger.warn("Can't find project for dir $dir")
          null
        }
      }.toSet()
      Pair(name, deps)
    }.toMap()
    return@withContext ProjectStructureInfo(dependencies = deps, membersToWorkspace = emptyMap()) // No workspace info (yet)
  }

  @RequiresBackgroundThread
  private fun getDependencies(rootDir: Directory, projectToml: PyProjectToml): Set<Directory> {
    val depsFromFile = projectToml.project?.dependencies?.project ?: emptyList()
    val moduleDependencies = depsFromFile
      .mapNotNull { depSpec ->
        val match = PEP_621_PATH_DEPENDENCY.matchEntire(depSpec) ?: return@mapNotNull null
        val (_, depUri) = match.destructured
        return@mapNotNull parseDepUri(depUri)
      }

    val oldStyleModuleDependencies = projectToml.toml.getTableOrEmpty("tool.poetry.dependencies")
      .toMap().entries
      .mapNotNull { (_, depSpec) ->
        if (depSpec !is TomlTable || depSpec.getBoolean("develop") != true) return@mapNotNull null
        depSpec.getString("path")?.let { rootDir.resolve(it).normalize() }
      }
    return moduleDependencies.toSet() + oldStyleModuleDependencies.toSet()
  }
}


// e.g. "lib @ file:///home/user/projects/main/lib"
private val PEP_621_PATH_DEPENDENCY = """([\w-]+) @ (file:.*)""".toRegex()

private val logger = fileLogger()
private fun parseDepUri(depUri: String): Path? =
  try {
    URI(depUri).toPath()
  }
  catch (e: InvalidPathException) {
    logger.info("Dep $depUri points to wrong path", e)
    null
  }
  catch (e: URISyntaxException) {
    logger.info("Dep $depUri can't be parsed", e)
    null
  }

