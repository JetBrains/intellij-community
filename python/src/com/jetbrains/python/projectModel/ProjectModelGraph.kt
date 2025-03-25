// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel

import com.intellij.util.containers.addIfNotNull
import com.jetbrains.python.projectModel.poetry.PoetryConstants
import com.jetbrains.python.sdk.poetry.PY_PROJECT_TOML
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div
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
      val pyprojectToml = dir.resolve(PY_PROJECT_TOML)
      if (pyprojectToml.exists()) {
        val projectRoot =
          if (dir.resolve("uv.lock").exists() || Toml.parse(pyprojectToml).getTable("tool.uv.sources") != null) {
            readUvProjectRoot(dir)
          }
          else if (dir.resolve("poetry.lock").exists() || Toml.parse(pyprojectToml).getTable("tool.poetry") != null) {
            readPoetryProjectRoot(dir)
          }
          else {
            readUvProjectRoot(dir) ?: readPoetryProjectRoot(dir)
          }
        roots.addIfNotNull(projectRoot)
        return@onPreVisitDirectory FileVisitResult.SKIP_SUBTREE
      }
      return@onPreVisitDirectory FileVisitResult.CONTINUE
    }
  }
  return ProjectModelGraph(roots)
}

@OptIn(ExperimentalPathApi::class)
fun readPoetryProjectRoot(projectRoot: Path): ProjectModelRoot? {
  val modules = projectRoot.walk()
    .filter { it.name == PoetryConstants.PYPROJECT_TOML }
    .mapNotNull(::readPoetryPyProjectToml)
    .toList()
  if (modules.isNotEmpty()) {
    return ProjectModelRoot(
      root = projectRoot,
      modules = modules
    )
  }
  return null
}

private fun readPoetryPyProjectToml(pyprojectTomlPath: Path): ModuleDescriptor? {
  // TODO read editable dependencies in newer pyproject.toml
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
  val projectName = pyprojectToml.getString("tool.poetry.name")
  if (projectName == null) {
    return null
  }
  return ModuleDescriptor(projectName, pyprojectTomlPath.parent, moduleDependencies)
}

@OptIn(ExperimentalPathApi::class)
fun readUvProjectRoot(projectRoot: Path): ProjectModelRoot? {
  val rootPyProjectToml = Toml.parse(projectRoot / "pyproject.toml")
  val modules: List<ModuleDescriptor>
  val rootWorkspaceTable = rootPyProjectToml.getTable("tool.uv.workspace")
  //if (rootWorkspaceTable == null) {
    modules = projectRoot.walk()
      .filter { it.name == PoetryConstants.PYPROJECT_TOML }
      .mapNotNull(::readUvPyProjectToml)
      .toList()
  /*}
  else {
    val includeGlobs = rootWorkspaceTable.getArrayOrEmpty("members")
      .toList()
      .mapNotNull { it as? String }
      .map { getPathMatcher(it) }
    val excludeGlobs = rootWorkspaceTable.getArrayOrEmpty("exclude")
      .toList()
      .mapNotNull { it as? String }
      .map { getPathMatcher(it) }

    modules = mutableListOf()
    projectRoot.visitFileTree {
      onPreVisitDirectory { dir, _ ->
        if (dir == projectRoot) {
          val rootPyProjectToml = readUvPyProjectToml(dir.resolve(PY_PROJECT_TOML))
          if (rootPyProjectToml != null) {
            modules.add(rootPyProjectToml)
            return@onPreVisitDirectory FileVisitResult.CONTINUE
          }
          return@onPreVisitDirectory FileVisitResult.SKIP_SUBTREE
        }
        if (excludeGlobs.any { it.matches(dir.relativeTo(projectRoot)) }) {
          return@onPreVisitDirectory FileVisitResult.SKIP_SUBTREE
        }
        if (includeGlobs.any { it.matches(dir.relativeTo(projectRoot)) }) {
          val pyprojectToml = dir.resolve(PY_PROJECT_TOML)
          if (pyprojectToml.exists()) {
            modules.addIfNotNull(readUvPyProjectToml(pyprojectToml))
          }
          // TODO check if nested workspace members are allowed
          return@onPreVisitDirectory FileVisitResult.SKIP_SUBTREE
        }
        return@onPreVisitDirectory FileVisitResult.CONTINUE
      }
    }
  }*/
  return ProjectModelRoot(
    root = projectRoot,
    modules = modules
  )
}

private fun readUvPyProjectToml(pyprojectTomlPath: Path): ModuleDescriptor? {
  val pyprojectToml = Toml.parse(pyprojectTomlPath)
  val moduleDependencies: List<String> = pyprojectToml.getTableOrEmpty("tool.uv.sources")
    .toMap().entries
    .mapNotNull { (depName, depSpec) ->
      if (depSpec is TomlTable) {
        if (depSpec.getBoolean("workspace") == true) {
          // TODO check if it points to a real workspace member
          return@mapNotNull depName
        }
        if (depSpec.getBoolean("editable") == true) {
          val depPath = depSpec.getString("path")?.let { pyprojectTomlPath.parent.resolve(it) }
          if (depPath != null && depPath.isDirectory() && depPath.resolve(PoetryConstants.PYPROJECT_TOML).exists()) {
            return@mapNotNull depName
          }
        }
      }
      return@mapNotNull null
    }
  val projectName = pyprojectToml.getString("project.name")
  if (projectName == null) {
    return null
  }
  return ModuleDescriptor(projectName, pyprojectTomlPath.parent, moduleDependencies)
}
