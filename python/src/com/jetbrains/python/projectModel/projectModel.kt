// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel

import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.visitFileTree

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
data class ModuleDescriptor(val name: String, val root: Path, val moduleDependencies: List<ModuleDependency>)

data class ModuleDependency(val name: String, val path: Path)

interface PythonProjectRootResolver {
   fun discoverProjectRoot(directory: Path): ProjectModelRoot?
}

@OptIn(ExperimentalPathApi::class)
fun readProjectModelGraph(ijProjectRoot: Path, resolver: PythonProjectRootResolver): ProjectModelGraph {
  val roots = mutableListOf<ProjectModelRoot>()
  ijProjectRoot.visitFileTree {
    onPreVisitDirectory { dir, _ ->
      val buildSystemRoot = resolver.discoverProjectRoot(dir)
      if (buildSystemRoot != null) {
        roots.add(buildSystemRoot)
        return@onPreVisitDirectory FileVisitResult.SKIP_SUBTREE
      }
      return@onPreVisitDirectory FileVisitResult.CONTINUE
    }
  }
  
  return ProjectModelGraph(roots)
}