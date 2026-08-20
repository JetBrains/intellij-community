// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.getPathMatcher
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.PyProjectIssue
import com.intellij.python.pyproject.PyProjectTable
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.ProjectDependencies
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectCreator
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.PySdkDependencyGroupSupport
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.intellij.python.pyproject.psi.spi.PyProjectTomlPathValue
import com.intellij.python.pyproject.psi.spi.isPathDependencyKey
import com.intellij.python.pyproject.safeGet
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.backend.runtime.createUvToolRuntime
import com.intellij.python.uv.backend.runtime.uvCli
import com.intellij.python.uv.common.UV_TOOL_ID
import com.intellij.python.uv.common.UV_UI_INFO
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.ToolBasedProjectCreator
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.TomlArray
import org.apache.tuweni.toml.TomlInvalidTypeException
import org.apache.tuweni.toml.TomlTable
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.io.path.relativeTo


internal class UvPyProjectManager : PyProjectManager, PyProjectCreator by ToolBasedProjectCreator(
  object : ToolBasedProjectCreator.PyToolFuns {
    override suspend fun createRuntime(fs: EelFileSystem, where: Directory): Result<PyToolRuntime, PyError> {
      val tool = UvPyTool.getInstance().resolveExecutable(fs)
                 ?: return PyResult.localizedError(PySdkBundle.message("path.validation.file.not.found", "uv"))
      return Result.success(createUvToolRuntime(tool.path))
    }

    override suspend fun createProject(name: @NlsSafe String?, runtime: PyToolRuntime, where: Directory): PyResult<*> =
      runtime.uvCli().init(name)
  }
) {

  override val id: ToolId = UV_TOOL_ID

  override val ui: PyToolUIInfo = UV_UI_INFO

  override val flavorDataType: Class<UvSdkFlavor> = UvSdkFlavor::class.java

  override val dependencyGroupSupport: PySdkDependencyGroupSupport = UvDependencyGroupSupport

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
    // PY-91089: use safeGet, not TomlTable.getTable, which throws TomlInvalidTypeException when the key
    // holds a non-table value (e.g. the `[[tool.uv.sources]]` array-of-tables typo). Such a member is
    // simply treated as having no sources instead of aborting the whole workspace model sync.
    val memberToUvSourceTable = entries
      .mapNotNull { (projectName, toml) ->
        toml.pyProjectToml.toml.safeGet<TomlTable>(UV_SOURCES, unquotedDottedKey = true).successOrNull?.let { projectName to it }
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
      // but siblings use deduped module names (e.g. "lib@1"). Match by base name, and compare
      // names in their normalized form so a member published under its normalized name (e.g. abc-rag)
      // still matches a dependency spelled with '.'/'_' (e.g. abc.rag) (PY-89677).
      val siblingsByNormalizedName = siblings.associateBy { PyPackageName.normalizeProjectName(it.name.substringBefore('@')) }
      val resolvedWorkspaceDeps =
        workspaceDeps.mapNotNull { siblingsByNormalizedName[PyPackageName.normalizeProjectName(it.name)] }.toSet()
      val brokenDeps = workspaceDeps.filter { PyPackageName.normalizeProjectName(it.name) !in siblingsByNormalizedName }.toSet()
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
    TomlDependencySpecification.PathDependency(UV_SOURCES),
    TomlDependencySpecification.Pep621Dependency("tool.uv.dev-dependencies"),
  )

  /**
   * uv path values, made navigable by `PyProjectTomlPathReferenceContributor` (PY-90384):
   *
   * ```toml
   * [tool.uv.workspace]
   * members = ["sub-projects/sub-project-a"]  # -> the member directory
   *
   * [tool.uv.sources]
   * vendored = { path = "vendor/vendored-lib" }
   * ```
   *
   * All three spellings of the workspace table (`[tool.uv.workspace]`, `[tool.uv] workspace = { … }` and the
   * dotted `workspace.members`) arrive here as the same key path, so one comparison covers them.
   */
  override fun resolveTomlPath(keyPath: List<String>): PyProjectTomlPathValue? = when {
    keyPath == WORKSPACE_MEMBERS_KEY || keyPath == WORKSPACE_EXCLUDE_KEY -> PyProjectTomlPathValue()
    // A path source may point at an sdist / wheel rather than at a project directory.
    getTomlDependencySpecifications().isPathDependencyKey(keyPath) -> PyProjectTomlPathValue(acceptFiles = true)
    else -> null
  }

  override fun getAlternativeProjectTable(
    pyProjectToml: TomlTable,
    fallbackName: String,
    issues: MutableList<PyProjectIssue>,
  ): PyProjectTable? {
    val workspace = try {
      // virtual workspace
      pyProjectToml.getTable(UV_WORKSPACE)
    }
    catch (_: TomlInvalidTypeException) {
      null
    } != null
    return if (workspace) PyProjectTable.makeVirtProj(fallbackName) else null
  }
}

private const val UV_SOURCES = "tool.uv.sources"
private const val UV_WORKSPACE = "tool.uv.workspace"
private val WORKSPACE_MEMBERS_KEY = "$UV_WORKSPACE.members".split('.')
private val WORKSPACE_EXCLUDE_KEY = "$UV_WORKSPACE.exclude".split('.')

// Slightly more permissive than PEP 508 IDENTIFIER (allows leading underscores & consecutive separators),
// but sufficient here since dependency names are already validated by uv.
private val DEPENDENCY_NAME_REGEX = """^\s*(\w([\w\-.]*\w)?).*$""".toRegex()

private fun extractDependencyNamesWithoutExtras(toml: PyProjectToml): Set<String> =
  toml.allDeclaredDeps.mapNotNull {
    val (dependencyName, _) = DEPENDENCY_NAME_REGEX.matchEntire(it)?.destructured ?: return@mapNotNull null
    dependencyName
  }.toSet()

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
  // PY-91089: safeGet instead of getTable/getArrayOrEmpty, which throw TomlInvalidTypeException when
  // the key holds an unexpected type (e.g. the `[[tool.uv.workspace]]` array typo, or `members = "x"`).
  val workspace = toml.safeGet<TomlTable>(UV_WORKSPACE, unquotedDottedKey = true).successOrNull ?: return null
  val members = workspace.safeGet<TomlArray>("members", unquotedDottedKey = false).successOrNull?.asMatchers ?: emptyList()
  val exclude = workspace.safeGet<TomlArray>("exclude", unquotedDottedKey = false).successOrNull?.asMatchers ?: emptyList()
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
  val deps = extractDependencyNamesWithoutExtras(pyProject.pyProjectToml)
  // Dependency names indexed by their normalized form; a `tool.uv.sources` entry applies to a
  // dependency when their normalized names match, regardless of `-`/`_`/`.` spelling (PY-90207).
  val depByNormalizedName = deps.associateByTo(HashMap()) { PyPackageName.normalizeProjectName(it) }
  val workspaceDeps = mutableListOf<ProjectName>()
  val pathDeps = hashSetOf<Path>()
  for ((sourcesTable, ownerRoot) in sourcesTablesWithRoots) {
    for ((sourceKey, sourceValue) in sourcesTable.toMap().entries) {
      val normalizedKey = PyPackageName.normalizeProjectName(sourceKey)
      val depName = depByNormalizedName[normalizedKey] ?: continue

      // A source entry is either a single table or an array of tables (multiple sources selected by
      // platform marker — a valid uv feature); resolve every table it contains (PY-91195).
      val depTables = when (sourceValue) {
        is TomlTable -> listOf(sourceValue)
        is TomlArray -> sourceValue.toList().filterIsInstance<TomlTable>()
        else -> continue
      }

      var resolved = false
      for (table in depTables) {
        // PY-91089: safeGet instead of getBoolean/getString, which throw when the value has an unexpected type.
        if (table.safeGet<Boolean>("workspace", unquotedDottedKey = false).successOrNull == true) {
          workspaceDeps.add(ProjectName(depName))
          resolved = true
        }
        else {
          val path = table.safeGet<String>("path", unquotedDottedKey = false).successOrNull ?: continue
          try {
            pathDeps.add(ownerRoot.resolve(path).normalize())
            resolved = true
          }
          catch (e: InvalidPathException) {
            logger.info("Can't resolve $path against $ownerRoot", e)
          }
        }
      }
      // Once resolved by a higher-priority table, don't let parent workspace tables override it.
      if (resolved) {
        depByNormalizedName.remove(normalizedKey)
      }
    }
  }
  return DependencyInfo(workspaceDeps = workspaceDeps.toSet(), pathDeps = pathDeps)
}