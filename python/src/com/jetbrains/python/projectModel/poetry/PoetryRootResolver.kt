// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.jetbrains.python.projectModel.ModuleDependency
import com.jetbrains.python.projectModel.ModuleDescriptor
import com.jetbrains.python.projectModel.ProjectModelRoot
import com.jetbrains.python.projectModel.PythonProjectRootResolver
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
object PoetryRootResolver : PythonProjectRootResolver {
  override fun discoverProjectRoot(directory: Path): ProjectModelRoot? {
    if (!directory.resolve(PoetryConstants.PYPROJECT_TOML).exists()) {
      return null
    }
    val poetryProjects = directory.walk()
      .filter { it.name == PoetryConstants.PYPROJECT_TOML }
      .mapNotNull(::readPoetryPyProjectToml)
      .toList()
    if (poetryProjects.isNotEmpty()) {
      val modules = poetryProjects
        .map {
          ModuleDescriptor(
            name = it.projectName,
            root = it.root,
            moduleDependencies = it.editablePathDependencies.map { entry ->
              ModuleDependency(entry.key, entry.value)
            })
        }
      return ProjectModelRoot(
        root = directory,
        modules = modules
      )
    }
    return null
  }

  private fun readPoetryPyProjectToml(pyprojectTomlPath: Path): PoetryPyProjectToml? {
    // TODO read editable dependencies in newer pyproject.toml
    val pyprojectToml = Toml.parse(pyprojectTomlPath)
    val projectName = pyprojectToml.getString("tool.poetry.name") ?: pyprojectToml.getString("project.name")
    if (projectName == null) {
      return null
    }

    // Editable path dependencies can appear only inside tool.poetry.dependencies
    val moduleDependencies: Map<String, Path> = pyprojectToml.getTableOrEmpty("tool.poetry.dependencies")
      .toMap().entries
      .mapNotNull { (depName, depSpec) ->
        if (depSpec is TomlTable && depSpec.getBoolean("develop") == true) {
          val depPath = depSpec.getString("path")?.let { pyprojectTomlPath.parent.resolve(it).normalize() }
          if (depPath != null && depPath.isDirectory() && depPath.resolve(PoetryConstants.PYPROJECT_TOML).exists()) {
            return@mapNotNull depName to depPath
          }
        }
        return@mapNotNull null
      }
      .toMap()

    return PoetryPyProjectToml(
      projectName = projectName,
      root = pyprojectTomlPath.parent,
      editablePathDependencies = moduleDependencies
    )
  }

  private data class PoetryPyProjectToml(
    val projectName: String,
    val root: Path,
    val editablePathDependencies: Map<String, Path>,
  )
}