// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.jetbrains.python.Result
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.resolveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlParseResult
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Stores the file name of `pyproject.toml`.
 */
@Internal
const val PY_PROJECT_TOML: String = "pyproject.toml"


/**
 * Represents an issue that could occur in [PyProjectToml.parse].
 */
@Internal
sealed class PyProjectIssue {

  /**
   * Signifies that the version is missing from the `project` section, while also being absent from the `dynamic` array.
   */
  data object MissingVersion : PyProjectIssue()

  /**
   * Wraps [TomlTableSafeGetError].
   */
  @ConsistentCopyVisibility
  data class SafeGetError internal constructor(val error: TomlTableSafeGetError) : PyProjectIssue()

  /**
   * Signifies that a contact misses both `name` and `email` fields.
   */
  @ConsistentCopyVisibility
  data class InvalidContact internal constructor(val path: String) : PyProjectIssue()
}

/**
 * A general handler for `pyproject.toml` files.
 */
@ConsistentCopyVisibility
@Internal
data class PyProjectToml internal constructor(
  /**
   * Represents the parsed `pyproject.toml` file.
   */
  val project: PyProjectTable,

  /**
   * A list of issues that occurred during the execution of [PyProjectToml.parse].
   */
  val issues: List<PyProjectIssue>,

  /**
   * An instance of [TomlTable] provided by the TOML parser.
   */
  val toml: TomlParseResult,

  /**
   * PEP 735 groups (`dependency-groups`) mapped to their dependencies.
   *
   * Every declared key is present, so a group may map to an empty list: either its array is empty, or it only holds
   * still-unsupported `{include-group = "..."}` tables. A group is user-visible as soon as its key exists.
   */
  val dependencyGroups: PyProjectDependencyTable,
) {
  /**
   * Every dependency declared anywhere in this file: `project.dependencies`, the
   * `project.optional-dependencies` extras, and the PEP 735 [dependencyGroups] (PY-91629).
   */
  val allDeclaredDeps: Set<String> = dependencyGroups.allDeps + project.dependencies.requiredAndExtras

  /**
   * Gets a specific tool from an object implementing [PyProjectToolFactory].
   *
   * Example:
   *
   * ```kotlin
   * val pyProject = PyProjectToml.parse(psiFile.virtualFile.inputStream).orThrow()
   * val uvTool = pyProject.getTool(UvPyProject)
   * val hatch = pyProject.getTool(HatchPyProject)
   * ```
   */
  fun <T : PyProjectToolFactory<U>, U> getTool(tool: T): U {
    return tool.createTool(mapOf(*tool.tables.map {
      it to toml.getTable(it)
    }.toTypedArray()))
  }

  /**
   * Returns dependency group names, in order: "main" (representing `[project.dependencies]`), then [toolSpecificGroups],
   * then PEP 735 `[dependency-groups]` keys, then PEP 621 `[project.optional-dependencies]` keys.
   *
   * A name declared in several places (say, both a PEP 735 group and a PEP 621 extra) is reported once, at its
   * earliest position.
   *
   * @param toolSpecificGroups tool-specific group names the caller contributes, e.g. Poetry's legacy `dev` and its
   * `[tool.poetry.group]` keys, which no PEP describes.
   */
  @Internal
  fun getDependencyGroupNames(toolSpecificGroups: List<String> = emptyList()): List<String> {
    val extraGroups = dependencyGroups.groupNames
    val optionalGroups = project.dependencies.extras.groupNames
    return buildList {
      addAll(DEFAULT_GROUP_NAMES)
      addAll(toolSpecificGroups)
      addAll(extraGroups)
      addAll(optionalGroups)
    }.distinct()
  }

  companion object {
    @Internal
    private val DEFAULT_GROUP_NAMES: List<String> = listOf(PY_PROJECT_DEFAULT_GROUP)
    private val CACHE_KEY = Key.create<CachedValue<PyProjectToml>>("PyProjectTomlCache")

    /**
     * Parses and caches [pyProjectFile] content. Cache is invalidated automatically when the file changes.
     */
    @Internal
    suspend fun parseCached(project: Project, pyProjectFile: VirtualFile): PyProjectToml? {
      return readAction {
        val psiFile = PsiManager.getInstance(project).findFile(pyProjectFile) ?: return@readAction null
        CachedValuesManager.getManager(project).getCachedValue(psiFile, CACHE_KEY, {
          CachedValueProvider.Result.create(parse(psiFile.text, psiFile.parent?.name ?: project.name), psiFile)
        }, false)
      }
    }

    /**
     * Attempts to parse [tomlFileContent] and construct an instance of [PyProjectToml].
     * In case of serious errors (e.g. `[project]` exists, but has  no `name`) returns `null`. O
     * therwise, returns an object with data and issues.
     *
     * It also supports "virtual projects" without `[project]` section at all ([fallbackName] is used then), but only if
     * [PyProjectManager.canBeVirtualProject]
     *
     * Example:
     *
     * ```kotlin
     * val pyProject = PyProjectToml.parse(psiFile.virtualFile.inputStream).orThrow()
     * val uvTool = pyProject.getTool(UvPyProject)
     * val hatch = pyProject.getTool(HatchPyProject)
     * ```
     */
    fun parse(tomlFileContent: String, fallbackName: String): PyProjectToml? {
      val issues = mutableListOf<PyProjectIssue>()
      val toml = Toml.parse(tomlFileContent)


      val depGroups = toml
        .safeGet<TomlTable>(PY_PROJECT_TOML_DEPENDENCY_GROUPS, unquotedDottedKey = false)
        .getOrIssue(issues)

      val depsFromGroups = depGroups?.keySet()?.associate { depGroupName ->
        // Can't filter by string because there might be (still unsupported) { include-group = "" } tables
        depGroupName to (depGroups.safeGetArr<Any>(depGroupName, unquotedDottedKey = false)
                           .getOrIssue(issues)?.filterIsInstance<String>() ?: emptyList())
      }

      val projectTomlTable = toml.safeGet<TomlTable>(PY_PROJECT_TOML_PROJECT, unquotedDottedKey = false).getOrIssue(issues)
      val pyProjectTable =
        when {
          // toml file has `[project]` section
          projectTomlTable != null -> PyProjectTable.make(projectTomlTable, issues)
          // toml file has no `[project]` but might be virtual project
          PyProjectManager.EP.extensionList.any { it.canBeVirtualProject(toml) } -> PyProjectTable.makeVirtProj(fallbackName)
          else -> null
        } ?: return null

      return PyProjectToml(
        pyProjectTable,
        issues,
        toml,
        dependencyGroups = PyProjectDependencyTable(depsFromGroups ?: emptyMap()),
      )
    }

    private val logger = fileLogger()

    /**
     * Same as [parse] but returns `null` if fail can't be read
     */
    suspend fun parseOrNull(file: Path): PyProjectToml? {
      val content = try {
        withContext(Dispatchers.IO) {
          file.readText()
        }
      }
      catch (e: IOException) {
        logger.debug(e) { "Error reading $file" }
        return null
      }
      return parse(content, file.parent?.name ?: file.name) // Rarely file might have no parent
    }

    /**
     * Attempts to find the `pyproject.toml` file in the provided module.
     * Returns null if not found.
     */
    suspend fun findPyProjectTomlFile(module: Module): PyProjectTomlFile? {
      return module.asPyProject()?.resolveFile(PY_PROJECT_TOML)?.let { LocalFileSystem.getInstance().findFileByNioFile(it) }
        ?.let { PyProjectTomlFile(it) }
    }
  }
}

internal fun <T> Result<T, TomlTableSafeGetError>.getOrIssue(issues: MutableList<PyProjectIssue>, onNull: (() -> Unit)? = null) =
  getOrIssue(issues, { PyProjectIssue.SafeGetError(it) }, onNull)