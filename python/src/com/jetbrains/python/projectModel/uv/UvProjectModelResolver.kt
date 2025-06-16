// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.util.getPathMatcher
import com.jetbrains.python.projectModel.ExternalProject
import com.jetbrains.python.projectModel.ExternalProjectDependency
import com.jetbrains.python.projectModel.ExternalProjectGraph
import com.jetbrains.python.projectModel.PythonProjectModelResolver
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.io.path.*

private const val DEFAULT_VENV_DIR = ".venv"

data class UvProject(
  override val name: String,
  override val root: Path,
  override val dependencies: List<ExternalProjectDependency>,
  override val fullName: String?,
  val isWorkspace: Boolean,
  val parentWorkspace: UvProject?,
) : ExternalProject {
  override val sourceRoots: List<Path>
    get() = listOfNotNull((root / "src").takeIf { it.isDirectory() })
  override val excludedRoots: List<Path>
    get() = listOfNotNull((root / DEFAULT_VENV_DIR).takeIf { it.isDirectory() })
}

private data class UvPyProjectToml(
  val projectName: String,
  val root: Path,
  val workspaceDependencies: List<String>,
  val pathDependencies: Map<String, Path>,
  val workspaceMemberPathMatchers: List<PathMatcher>,
  val workspaceExcludePathMatchers: List<PathMatcher>,
) {
  val isWorkspace = workspaceMemberPathMatchers.isNotEmpty()
}

@OptIn(ExperimentalPathApi::class)
object UvProjectModelResolver : PythonProjectModelResolver<UvProject> {
  override fun discoverProjectRootSubgraph(root: Path): ExternalProjectGraph<UvProject>? {
    if (!root.resolve(UvConstants.PYPROJECT_TOML).exists()) {
      return null
    }
    val workspaceMembers = mutableMapOf<UvPyProjectToml, MutableMap<String, UvPyProjectToml>>()
    val standaloneProjects = mutableListOf<UvPyProjectToml>()
    val workspaceStack = ArrayDeque<UvPyProjectToml>()
    root.visitFileTree {
      onPreVisitDirectory { dir, _ ->
        if (dir.name == DEFAULT_VENV_DIR) {
          return@onPreVisitDirectory FileVisitResult.SKIP_SUBTREE
        }
        val projectToml = readUvPyProjectToml(dir / "pyproject.toml")
        if (projectToml == null) {
          return@onPreVisitDirectory FileVisitResult.CONTINUE
        }
        if (projectToml.isWorkspace) {
          workspaceStack.add(projectToml)
          workspaceMembers.put(projectToml, mutableMapOf())
          return@onPreVisitDirectory FileVisitResult.CONTINUE
        }
        if (workspaceStack.isNotEmpty()) {
          val closestWorkspace = workspaceStack.last()
          val relProjectPath = projectToml.root.relativeTo(closestWorkspace.root)
          val isWorkspaceMember = closestWorkspace.workspaceExcludePathMatchers.none { it.matches(relProjectPath) } &&
                                  closestWorkspace.workspaceMemberPathMatchers.any { it.matches(relProjectPath) }
          if (isWorkspaceMember) {
            workspaceMembers[closestWorkspace]!!.put(projectToml.projectName, projectToml)
            return@onPreVisitDirectory FileVisitResult.CONTINUE
          }
        }
        standaloneProjects.add(projectToml)
        return@onPreVisitDirectory FileVisitResult.CONTINUE
      }

      onPostVisitDirectory { dir, _ ->
        if (workspaceStack.lastOrNull()?.root == dir) {
          workspaceStack.removeLast()
        }
        FileVisitResult.CONTINUE
      }
    }
    val allUvProjects = mutableListOf<UvProject>()
    standaloneProjects.mapTo(allUvProjects) {
      UvProject(
        name = it.projectName,
        root = it.root,
        dependencies = it.pathDependencies.map { dep -> ExternalProjectDependency(name = dep.key, path = dep.value) },
        isWorkspace = false,
        parentWorkspace = null,
        fullName = it.projectName,
      )
    }

    for ((wsRootToml, wsMembersByNames) in workspaceMembers) {
      fun resolved(wsDependencies: List<String>): Map<String, Path> {
        return wsDependencies.mapNotNull { name -> wsMembersByNames[name]?.let { name to it.root } }.toMap()
      }
      val wsRootProject = UvProject(
        name = wsRootToml.projectName,
        root = wsRootToml.root,
        dependencies = (wsRootToml.pathDependencies + resolved(wsRootToml.workspaceDependencies))
          .map { ExternalProjectDependency(name = it.key, path = it.value) },
        isWorkspace = true,
        parentWorkspace = null,
        fullName = wsRootToml.projectName,
      )
      allUvProjects.add(wsRootProject)
      for ((_, wsMemberToml) in wsMembersByNames) {
        allUvProjects.add(UvProject(
          name = wsMemberToml.projectName,
          root = wsMemberToml.root,
          dependencies = (wsMemberToml.pathDependencies + resolved(wsMemberToml.workspaceDependencies))
            .map { ExternalProjectDependency(name = it.key, path = it.value) },
          isWorkspace = false,
          parentWorkspace = wsRootProject,
          fullName = "${wsRootProject.name}:${wsMemberToml.projectName}"
        ))
      }
    }
    return ExternalProjectGraph(root = root, projects = allUvProjects)
  }

  private fun readUvPyProjectToml(pyprojectTomlPath: Path): UvPyProjectToml? {
    if (!(pyprojectTomlPath.exists())) {
      return null
    }
    val pyprojectToml = Toml.parse(pyprojectTomlPath)
    val projectName = pyprojectToml.getString("project.name")
    if (projectName == null) {
      return null
    }
    val workspaceTable = pyprojectToml.getTable("tool.uv.workspace")
    val includeGlobs: List<PathMatcher> 
    val excludeGlobs: List<PathMatcher>
    if (workspaceTable != null) {
      includeGlobs = workspaceTable.getArrayOrEmpty("members")
        .toList()
        .filterIsInstance<String>()
        .map { getPathMatcher(it) }
      excludeGlobs = workspaceTable.getArrayOrEmpty("exclude")
        .toList()
        .filterIsInstance<String>()
        .map { getPathMatcher(it) }
    }
    else {
      includeGlobs = emptyList()
      excludeGlobs = emptyList()
    }
    val workspaceDependencies = mutableListOf<String>()
    val editablePathDependencies = mutableMapOf<String, Path>()
    pyprojectToml.getTableOrEmpty("tool.uv.sources")
      .toMap().entries
      .forEach { (depName, depSpec) ->
        if (depSpec is TomlTable) {
          if (depSpec.getBoolean("workspace") == true) {
            workspaceDependencies.add(depName)
          }
          else if (depSpec.getBoolean("editable") == true) {
            val depPath = depSpec.getString("path")?.let { pyprojectTomlPath.parent.resolve(it).normalize() }
            if (depPath != null && depPath.isDirectory() && (depPath / UvConstants.PYPROJECT_TOML).exists()) {
              editablePathDependencies[depName] = depPath
            }
          }
        }
      }
    return UvPyProjectToml(
      projectName = projectName,
      root = pyprojectTomlPath.parent,
      workspaceDependencies = workspaceDependencies,
      pathDependencies = editablePathDependencies,
      workspaceMemberPathMatchers = includeGlobs,
      workspaceExcludePathMatchers = excludeGlobs,
    )
  }
}