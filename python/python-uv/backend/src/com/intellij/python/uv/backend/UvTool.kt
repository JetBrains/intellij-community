// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.getPathMatcher
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.internal.pyProjectToml.TomlDependencySpecification
import com.intellij.python.pyproject.model.spi.ProjectDependencies
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.Tool
import com.intellij.python.uv.common.UV_TOOL_ID
import com.intellij.python.uv.common.UV_UI_INFO
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.TomlArray
import org.apache.tuweni.toml.TomlTable
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.io.path.relativeTo


internal class UvTool : Tool {

  override val id: ToolId = UV_TOOL_ID

  override val ui: PyToolUIInfo = UV_UI_INFO

  override suspend fun getProjectName(projectToml: TomlTable): @NlsSafe String? = null

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo = withContext(Dispatchers.Default) {
    val workspaces = entries.mapNotNull { (name, entry) ->
      val matchers = getWorkspaceMembers(entry.pyProjectToml.toml) ?: return@mapNotNull null
      Pair(entry.root, Pair(matchers, name))
    }.toMap()

    val dirToProjectName = rootIndex.entries.toList()
    val workspaceToMembers = HashMap<ProjectName, MutableSet<ProjectName>>()
    val memberToWorkspace = HashMap<ProjectName, MutableSet<ProjectName>>()
    for ((workspaceRoot, matchersAndName) in workspaces) {
      val (matchers, workspaceName) = matchersAndName
      // From the uv doc: every workspace needs a root, which is also a workspace member.
      val workspaceMembers = mutableSetOf(workspaceName)
      workspaceToMembers[workspaceName] = workspaceMembers
      memberToWorkspace[workspaceName] = mutableSetOf(workspaceName)
      for ((memberRoot, memberName) in dirToProjectName) {
        if (!memberRoot.startsWith(workspaceRoot)) continue

        if (matchers.match(memberRoot.relativeTo(workspaceRoot).normalize())) {
          workspaceMembers.add(memberName)
          memberToWorkspace.getOrPut(memberName) { HashSet() }.add(workspaceName)
        }

      }
    }

    // Each member might have tool.uv.sources table.
    val memberToUvSourceTable = entries
      .mapNotNull { (projectName, toml) ->
        toml.pyProjectToml.toml.getTable("tool.uv.sources")?.let { projectName to it }
      }
      .toMap()

    val dependencies = HashMap<ProjectName, Set<ProjectName>>()

    for ((name, projectToml) in entries) {
      val siblings = memberToWorkspace[name]?.mapNotNull { workspaceToMembers[it] }?.flatten()?.toSet() ?: continue
      // tool.uv.sources tables to consult, each paired with the pyproject root that defines it,
      // so `path = "..."` is resolved against the directory of the pyproject.toml that owns the table.
      // Project's own table comes first so it overrides parents on conflicts.
      val sourcesTablesWithRoots = buildList {
        memberToUvSourceTable[name]?.let { add(SourceTableWithOwner(it, projectToml.root)) }
        memberToWorkspace[name]?.forEach { workspaceName ->
          if (workspaceName == name) return@forEach
          val workspaceTable = memberToUvSourceTable[workspaceName] ?: return@forEach
          val workspaceRoot = entries[workspaceName]?.root ?: return@forEach
          add(SourceTableWithOwner(workspaceTable, workspaceRoot))
        }
      }
      val (workspaceDeps, pathDeps) = getUvDependencies(projectToml, sourcesTablesWithRoots) ?: continue
      // Workspace deps use natural package names from pyproject.toml (e.g. "lib"),
      // but siblings use deduped module names (e.g. "lib@1"). Match by base name.
      val siblingsByBaseName = siblings.associateBy { ProjectName(it.name.substringBefore('@')) }
      val resolvedWorkspaceDeps = workspaceDeps.mapNotNull { siblingsByBaseName[it] }.toSet()
      val brokenDeps = workspaceDeps.filter { it !in siblingsByBaseName }.toSet()
      if (brokenDeps.isNotEmpty()) {
        logger.info("Deps are broken: ${brokenDeps.joinToString(", ")}")
      }
      val pathDepsWithName = pathDeps.mapNotNull {
        rootIndex[it] ?: run {
          logger.info("No module at ${it}")
          null
        }
      }
      dependencies[name] = resolvedWorkspaceDeps + pathDepsWithName

    }
    return@withContext ProjectStructureInfo(
      dependencies = ProjectDependencies(dependencies),
      membersToWorkspace = memberToWorkspace.map { (member, workspaces) ->
        val workspaceCount = workspaces.size
        assert(workspaceCount != 0) { "Workspace can't be empty for $member" }
        if (workspaceCount > 1) {
          logger.warn("more than one workspace for member $member, will use the first one")
        }
        Pair(member, workspaces.first())
      }.toMap()
    )

  }

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = listOf(
    TomlDependencySpecification.PathDependency("tool.uv.sources"),
    TomlDependencySpecification.Pep621Dependency("tool.uv.dev-dependencies"),
  )
}

// Slightly more permissive than PEP 508 IDENTIFIER (allows leading underscores & consecutive separators),
// but sufficient here since dependency names are already validated by uv.
private val DEPENDENCY_NAME_REGEX = """^\s*(\w([\w\-.]*\w)?).*$""".toRegex()

private fun extractDependencyNamesWithoutExtras(toml: PyProjectToml): Set<String>? =
  toml.project?.dependencies?.let { it.project + it.allDepsFromGroups }?.mapNotNull {
    val (dependencyName, _) = DEPENDENCY_NAME_REGEX.matchEntire(it)?.destructured ?: return@mapNotNull null
    dependencyName
  }?.toSet()

private data class WorkspaceInfo(val members: List<PathMatcher>, val exclude: List<PathMatcher>) {
  fun match(path: Path): Boolean =
    members.any { it.matchPath(path) } && exclude.none { it.matchPath(path) }

  /**
   * uv workspace members/exclude may use "./" prefixes in glob patterns (e.g., "./&#42;" or "./packages/&#42;"),
   * but Java's PathMatcher treats "./packages" and "packages" as different patterns.
   * Since member paths from relativeTo().normalize() never have "./" prefix,
   * we try both forms to handle either pattern style.
   */
  private fun PathMatcher.matchPath(path: Path): Boolean = matches(path) || matches(Path.of(".").resolve(path))
}

private val TomlArray.asMatchers: List<PathMatcher> get() = toList().filterIsInstance<String>().map { getPathMatcher(it) }
private val logger = fileLogger()

private data class DependencyInfo(val workspaceDeps: Set<ProjectName>, val pathDeps: Set<Directory>)

/** A `tool.uv.sources` TOML table together with the pyproject root that defines it; the root is the base for relative `path` sources. */
private data class SourceTableWithOwner(val table: TomlTable, val ownerRoot: Path)

@RequiresBackgroundThread
private fun getWorkspaceMembers(toml: TomlTable): WorkspaceInfo? {
  val workspace = toml.getTable("tool.uv.workspace") ?: return null
  val members = workspace.getArrayOrEmpty("members").asMatchers
  val exclude = workspace.getArrayOrEmpty("exclude").asMatchers
  if (members.isEmpty()) return null
  return WorkspaceInfo(members = members, exclude = exclude)
}

/**
 * Resolves `tool.uv.sources` entries that match [pyProject]'s declared dependencies.
 *
 * [sourcesTablesWithRoots] is the ordered list of source tables to consult, each paired with the pyproject root that defines it
 * (project's own table first, then each parent workspace). The first match wins, so the project's own declarations override the parents'.
 * Each `path = "..."` entry is resolved against its owning root, matching uv's "relative to the defining pyproject.toml" semantics.
 */
@RequiresBackgroundThread
private fun getUvDependencies(
  pyProject: PyProjectTomlProject,
  sourcesTablesWithRoots: List<SourceTableWithOwner>,
): DependencyInfo? {
  if (sourcesTablesWithRoots.isEmpty()) {
    return null
  }
  val deps = extractDependencyNamesWithoutExtras(pyProject.pyProjectToml)?.toMutableSet() ?: return null
  val workspaceDeps = mutableListOf<ProjectName>()
  val pathDeps = hashSetOf<Path>()
  for ((sourcesTable, ownerRoot) in sourcesTablesWithRoots) {
    for ((depName, depTable) in sourcesTable.toMap().entries) {
      if (depName !in deps) continue
      val table = depTable as? TomlTable ?: continue

      if (table.getBoolean("workspace") == true) {
        workspaceDeps.add(ProjectName(depName))
        deps.remove(depName)
      }
      else {
        val path = table.getString("path") ?: continue
        try {
          pathDeps.add(ownerRoot.resolve(path).normalize())
          deps.remove(depName)
        }
        catch (e: InvalidPathException) {
          logger.info("Can't resolve $path against $ownerRoot", e)
        }
      }
    }
  }
  return DependencyInfo(workspaceDeps = workspaceDeps.toSet(), pathDeps = pathDeps)
}