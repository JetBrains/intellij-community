// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.jetbrains.python.sdk.poetry.PY_PROJECT_TOML
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.visitFileTree
import kotlin.io.path.walk

/**
 * Represents a "forest" of non-overlapping project roots managed by a particular build-system, such as Poetry.
 * 
 * For instance, in the following structure
 * ```
 * root/
 *   project1/
 *     pyproject.toml
 *     lib1/
 *       pyproject.toml
 *     lib2/
 *       pyproject.toml
 *   project2/
 *     pyproject.toml
 * ```
 * `./project1` and `./project2` are considered project model roots, but not `./project1/lib1` or `./project1/lib2` 
 * because they are already under `project1`.
 */
data class ProjectModelGraph(val roots: List<ProjectModelRoot>)

/**
 * Represents a tree of project modules residing under a single detectable project root (e.g. containing a root pyproject.toml).
 * These modules might optionally depend on each other, but it's not a requirement.
 * 
 * In the following structure:
 *
 * ```
 * root/
 *   project1/
 *     pyproject.toml
 *     lib1/
 *       pyproject.toml
 *     lib2/
 *       pyproject.toml
 *   project2/
 *     pyproject.toml
 * ```
 * 
 * the project model root for `./project1` contains module descriptors for `./project1/pyproject.toml`,
 * `./project1/lib1/pyproject.toml` and `./project1/lib2/pyproject.toml`.
 */
data class ProjectModelRoot(val root: Path, val modules: List<ModuleDescriptor>)

/**
 * Defines a project module in a particular directory with its unique name, and a set of module dependencies
 * (usually editable Python path dependencies to other modules in the same IJ project).
 */
data class ModuleDescriptor(val name: String, val root: Path, val moduleDependencies: List<String>)

@OptIn(ExperimentalPathApi::class)
fun readProjectModelGraph(ijProjectRoot: Path): ProjectModelGraph {
  val roots = mutableListOf<ProjectModelRoot>()
  ijProjectRoot.visitFileTree {  
    onPreVisitDirectory { dir, _ ->
      if (dir.resolve(PY_PROJECT_TOML).exists()) {
        val projectRoot = readProjectModelRoot(dir)
        if (projectRoot != null) {
          roots.add(projectRoot)
        }
        return@onPreVisitDirectory FileVisitResult.SKIP_SUBTREE
      }
      return@onPreVisitDirectory FileVisitResult.CONTINUE
    }
  }
  return ProjectModelGraph(roots)
}

@OptIn(ExperimentalPathApi::class)
fun readProjectModelRoot(projectRoot: Path): ProjectModelRoot? {
  val modules = projectRoot.walk()
    .filter { it.name == PoetryConstants.PYPROJECT_TOML }
    .map(::readPoetryPyProjectToml)
    .toList()
  if (modules.isNotEmpty()) {
    return ProjectModelRoot(
      root = projectRoot,
      modules = modules
    )
  }
  return null
}

private fun readPoetryPyProjectToml(pyprojectTomlPath: Path): ModuleDescriptor {
  val pyprojectToml = Toml.parse(pyprojectTomlPath)
  val moduleDependencies: List<String> = pyprojectToml.getTableOrEmpty("tool.poetry.dependencies")
    .toMap().entries
    .mapNotNull { (depName, depSpec) ->
      if (depSpec is TomlTable && depSpec.getBoolean("develop") == true) {
        val depPath = depSpec.getString("path")?.let { pyprojectTomlPath.parent.resolve(it) }
        if (depPath != null && depPath.isDirectory() && depPath.resolve(PoetryConstants.PYPROJECT_TOML).exists()) {
          return@mapNotNull depName
        }
      }
      return@mapNotNull null
    }
  return ModuleDescriptor(pyprojectToml.getString("tool.poetry.name")!!, pyprojectTomlPath.parent, moduleDependencies)
}
