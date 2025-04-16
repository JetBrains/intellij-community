// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel

import com.intellij.openapi.util.io.FileUtil
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.visitFileTree

/**
 * Represents a graph of modules residing under a common root directory.
 * These modules might depend on each other, but it's not a requirement.
 * The root itself can be a valid module root, but it's not a requirement.
 */
data class ProjectModelGraph(val root: Path, val modules: List<ModuleDescriptor>)

/**
 * Defines a project module in a particular directory with its unique name, and a set of module dependencies
 * (usually editable Python path dependencies to other modules in the same IJ project).
 */
data class ModuleDescriptor(val name: String, val root: Path, val moduleDependencies: List<ModuleDependency>)

data class ModuleDependency(val name: String, val path: Path)

interface PythonProjectModelResolver {
  /**
   * If the `root` directory is considered a project root in a particular project management system
   * (e.g. it contains pyproject.toml or other such marker files), traverse it and return a subgraph describing modules
   * declared inside this root.
   * All these module roots should reside withing the `root` directory but their dependencies might be outside it.
   * The root directory of the graph should be `root`.
   *
   * For instance, in the following layout (assuming that pyproject.toml indicates a valid project root)
   * ```
   * libs/
   *   project1/
   *     pyproject.toml
   *   project2/
   *     pyproject.toml
   * ```
   * this method should return `null` for `libs/` but module graphs containing *only* modules `project1` and `project2`
   * for the directories `project1/` and `project2` respectively, even if there is a dependency between them.
   */
  fun discoverProjectRootSubgraph(root: Path): ProjectModelGraph?

  /**
   * Find all project model graphs within the given directory (presumably the root directory of an IJ project).
   * All these graphs are supposed to be independent components, i.e., they don't depend on each other's modules.
   * The roots of these modules might not themselves be valid modules, but just plain directories.
   *
   * For instance, in the following layout
   * ```
   * libs/
   *   project1/
   *     pyproject.toml
   *   project2/
   *     pyproject.toml
   * ```
   * If `project1` depends on `project2` (or vice-versa), this methods should return a single graph with its 
   * root in `libs/` containing modules for both `project1` and `project2`.
   * If these two projects are independents, there will be two graphs for `project1` and `project2` respectively.
   */
  @OptIn(ExperimentalPathApi::class)
  fun discoverIndependentProjectGraphs(root: Path): List<ProjectModelGraph> {
    val graphs = mutableListOf<ProjectModelGraph>()
    root.visitFileTree {
      onPreVisitDirectory { dir, _ ->
        val buildSystemRoot = discoverProjectRootSubgraph(dir)
        if (buildSystemRoot != null) {
          graphs.add(buildSystemRoot)
          return@onPreVisitDirectory FileVisitResult.SKIP_SUBTREE
        }
        return@onPreVisitDirectory FileVisitResult.CONTINUE
      }
    }

    // TODO make sure that roots doesn't leave ijProjectRoot boundaries
    return mergeRootsReferringToEachOther(graphs)
  }
}

interface ProjectModelSettings {
  fun getLinkedProjects(): List<Path>
  fun setLinkedProjects(projects: List<Path>)
  fun removeLinkedProject(projectRoot: Path)
  fun addLinkedProject(projectRoot: Path)
}

interface ProjectModelSyncListener {
  fun onStart(projectRoot: Path): Unit = Unit
  fun onFinish(projectRoot: Path): Unit = Unit
}

private fun mergeRootsReferringToEachOther(roots: MutableList<ProjectModelGraph>): List<ProjectModelGraph> {
  fun commonAncestorPath(paths: Iterable<Path>): Path {
    val normalized = paths.map { it.normalize() }
    return normalized.reduce { p1, p2 -> FileUtil.findAncestor(p1, p2)!! }
  }

  val expandedProjectRoots = roots.map { root ->
    val allModuleRootsAndDependencies = root.modules.asSequence()
      .flatMap { module -> listOf(module.root) + module.moduleDependencies.map { it.path } }
      .distinct()
      .toList()
    root.copy(root = commonAncestorPath(allModuleRootsAndDependencies))
  }

  val expandedProjectRootsByRootPath = expandedProjectRoots.sortedBy { it.root }
  val mergedProjectRoots = mutableListOf<ProjectModelGraph>()
  for (root in expandedProjectRootsByRootPath) {
    if (mergedProjectRoots.isEmpty()) {
      mergedProjectRoots.add(root)
    }
    else {
      val lastCluster = mergedProjectRoots.last()
      if (root.root.startsWith(lastCluster.root)) {
        mergedProjectRoots[mergedProjectRoots.lastIndex] = lastCluster.copy(modules = lastCluster.modules + root.modules)
      }
      else {
        mergedProjectRoots.add(root)
      }
    }
  }
  return mergedProjectRoots
}