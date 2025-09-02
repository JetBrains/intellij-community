// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.jetbrains.python.projectModel.ExternalProject
import com.jetbrains.python.projectModel.ExternalProjectDependency
import com.jetbrains.python.projectModel.ExternalProjectGraph
import com.jetbrains.python.projectModel.PythonProjectModelResolver
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.*

// e.g. "lib @ file:///home/user/projects/main/lib"
private val PEP_621_PATH_DEPENDENCY = """([\w-]+) @ (file:.*)""".toRegex()

data class PoetryProject(
  override val name: String,
  override val root: Path,
  override val dependencies: List<ExternalProjectDependency>,
) : ExternalProject {
  override val sourceRoots: List<Path>
    get() = listOfNotNull((root / "src").takeIf { it.isDirectory() })
  override val excludedRoots: List<Path>
    get() = emptyList()

  // Poetry projects don't have any declarative hierarchical structure
  override val fullName: String? 
    get() = name
}

@OptIn(ExperimentalPathApi::class)
object PoetryProjectModelResolver : PythonProjectModelResolver<PoetryProject> {
  override fun discoverProjectRootSubgraph(root: Path): ExternalProjectGraph<PoetryProject>? {
    if (!root.resolve(PoetryConstants.PYPROJECT_TOML).exists()) {
      return null
    }
    val poetryProjects = root.walk()
      .filter { it.name == PoetryConstants.PYPROJECT_TOML }
      .mapNotNull(::readPoetryPyProjectToml)
      .toList()
    if (poetryProjects.isNotEmpty()) {
      val modules = poetryProjects
        .map {
          PoetryProject(
            name = it.projectName,
            root = it.root,
            dependencies = it.editablePathDependencies.map { entry ->
              ExternalProjectDependency(entry.key, entry.value)
            })
        }
      return ExternalProjectGraph(
        root = root,
        projects = modules
      )
    }
    return null
  }

  private fun readPoetryPyProjectToml(pyprojectTomlPath: Path): PoetryPyProjectToml? {
    val pyprojectToml = Toml.parse(pyprojectTomlPath)
    val projectName = pyprojectToml.getString("tool.poetry.name") ?: pyprojectToml.getString("project.name")
    if (projectName == null) {
      return null
    }
    
    val moduleDependencies = pyprojectToml.getArrayOrEmpty("project.dependencies")
      .toList()
      .filterIsInstance<String>()
      .mapNotNull { depSpec ->
        val match = PEP_621_PATH_DEPENDENCY.matchEntire(depSpec)
        if (match == null) return@mapNotNull null
        val (depName, depUri) = match.destructured
        val depPath = runCatching { Path.of(URI(depUri)) }.getOrNull() ?: return@mapNotNull null
        if (!depPath.isDirectory() || !depPath.resolve(PoetryConstants.PYPROJECT_TOML).exists()) {
          return@mapNotNull null
        }
        return@mapNotNull depName to depPath
      }
      .toMap()

    val oldStyleModuleDependencies = pyprojectToml.getTableOrEmpty("tool.poetry.dependencies")
      .toMap().entries
      .mapNotNull { (depName, depSpec) ->
        if (depSpec !is TomlTable || depSpec.getBoolean("develop") != true) return@mapNotNull null
        val depPath = depSpec.getString("path")?.let { pyprojectTomlPath.parent.resolve(it).normalize() }
        if (depPath == null || !depPath.isDirectory() || !depPath.resolve(PoetryConstants.PYPROJECT_TOML).exists()) {
          return@mapNotNull null
        }
        return@mapNotNull depName to depPath
      }
      .toMap()

    return PoetryPyProjectToml(
      projectName = projectName,
      root = pyprojectTomlPath.parent,
      editablePathDependencies = moduleDependencies.ifEmpty { oldStyleModuleDependencies }
    )
  }

  private data class PoetryPyProjectToml(
    val projectName: String,
    val root: Path,
    val editablePathDependencies: Map<String, Path>,
  )
}