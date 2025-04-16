// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.util.getPathMatcher
import com.jetbrains.python.projectModel.ModuleDependency
import com.jetbrains.python.projectModel.ModuleDescriptor
import com.jetbrains.python.projectModel.ProjectModelGraph
import com.jetbrains.python.projectModel.PythonProjectModelResolver
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
object UvProjectModelResolver : PythonProjectModelResolver {
  override fun discoverProjectRootSubgraph(root: Path): ProjectModelGraph? {
    if (!root.resolve(UvConstants.PYPROJECT_TOML).exists()) {
      return null
    }
    val rootUvProject = readUvPyProjectToml(root / "pyproject.toml")
    if (rootUvProject == null) {
      return null
    }
    val workspaceMemberMatchers = rootUvProject.workspaceMemberGlobs.map { getPathMatcher(it) }
    val workspaceExcludeMatchers = rootUvProject.workspaceExcludeGlobs.map { getPathMatcher(it) }
    // TODO check if we can speed up traversal by not traversing further into workspace member directories
    // Can workspace members contain editable path dependencies inside?
    val allUvProjects: List<UvPyProjectToml> = root
      .walk()
      .filter { it.name == UvConstants.PYPROJECT_TOML }
      .mapNotNull(::readUvPyProjectToml)
      .toList()
    val workspaceMembers: Map<String, UvPyProjectToml>
    if (workspaceMemberMatchers.isEmpty()) {
      workspaceMembers = emptyMap()
    }
    else {
      workspaceMembers = allUvProjects
        .filter { uvProject ->
          if (uvProject == rootUvProject) return@filter true
          val relProjectPath = uvProject.root.relativeTo(root)
          return@filter workspaceExcludeMatchers.none { it.matches(relProjectPath) } 
                        && workspaceMemberMatchers.any { it.matches(relProjectPath) }
        }.associateBy { it.projectName }
    }
    
    return ProjectModelGraph(
      root = root,
      modules = allUvProjects
        .map { uvProject -> 
          val pathDependencies = uvProject.editablePathDependencies.map { ModuleDependency(it.key, it.value) }
          val resolvedWorkspaceDependencies = uvProject.workspaceDependencies.mapNotNull {
            val workspaceMember = workspaceMembers[it]
            if (workspaceMember != null) ModuleDependency(it, workspaceMember.root) 
            else null 
          }
          ModuleDescriptor(
            name = uvProject.projectName,
            root =uvProject.root,
            moduleDependencies = pathDependencies + resolvedWorkspaceDependencies
          )
        }
    )
  }

  private fun readUvPyProjectToml(pyprojectTomlPath: Path): UvPyProjectToml? {
    val pyprojectToml = Toml.parse(pyprojectTomlPath)
    val projectName = pyprojectToml.getString("project.name")
    if (projectName == null) {
      return null
    }
    val workspaceTable = pyprojectToml.getTable("tool.uv.workspace")
    val includeGlobs = mutableListOf<String>()
    val excludeGlobs = mutableListOf<String>()
    if (workspaceTable != null) {
      workspaceTable.getArrayOrEmpty("members")
        .toList()
        .mapNotNullTo(includeGlobs) { it as? String }
      workspaceTable.getArrayOrEmpty("exclude")
        .toList()
        .mapNotNullTo(excludeGlobs) { it as? String }
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
            if (depPath != null && depPath.isDirectory() && depPath.resolve(UvConstants.PYPROJECT_TOML).exists()) {
              editablePathDependencies[depName] = depPath
            }
          }
        }
      }
    return UvPyProjectToml(
      projectName = projectName,
      root = pyprojectTomlPath.parent,
      workspaceDependencies = workspaceDependencies,
      editablePathDependencies = editablePathDependencies,
      workspaceMemberGlobs = includeGlobs,
      workspaceExcludeGlobs = excludeGlobs,
    )
  }

  private data class UvPyProjectToml(
    val projectName: String,
    val root: Path,
    val workspaceDependencies: List<String>,
    val editablePathDependencies: Map<String, Path>,
    val workspaceMemberGlobs: List<String>,
    val workspaceExcludeGlobs: List<String>,
  )
}