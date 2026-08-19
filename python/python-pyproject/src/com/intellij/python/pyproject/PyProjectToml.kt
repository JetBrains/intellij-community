// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.python.Result
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.resolveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlInvalidTypeException
import org.apache.tuweni.toml.TomlParseResult
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.isRegularFile

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
  val depGroupsToDeps: Map<String, List<String>>,
) {
  /**
   * Values of [depGroupsToDeps]
   */
  val allDepsFromGroups: Set<String> = depGroupsToDeps.values.flatten().toSet()

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
    return tool.createTool(
      mapOf(
        *tool.tables.map {
          it to toml.getTable(it)
        }.toTypedArray()
      )
    )
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
    val extraGroups = depGroupsToDeps.keys
    val optionalGroups = project.dependencies.optional.keys.toList()
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
          CachedValueProvider.Result.create(parse(psiFile.text), psiFile)
        }, false)
      }
    }

    /**
     * Attempts to parse [tomlFileContent] and construct an instance of [PyProjectToml].
     * In case of serious errors (e.g. no `project.name`) returns `null`. Otherwise, returns an object with data and issues.
     *
     * Example:
     *
     * ```kotlin
     * val pyProject = PyProjectToml.parse(psiFile.virtualFile.inputStream).orThrow()
     * val uvTool = pyProject.getTool(UvPyProject)
     * val hatch = pyProject.getTool(HatchPyProject)
     * ```
     */
    fun parse(tomlFileContent: String): PyProjectToml? {
      val issues = mutableListOf<PyProjectIssue>()
      val toml = Toml.parse(tomlFileContent)


      val projectTable = toml.safeGet<TomlTable>(PY_PROJECT_TOML_PROJECT).getOrIssue(issues) ?: return null

      val name = try {
        projectTable.getString("name")
      }
                 catch (_: TomlInvalidTypeException) {
                   null
                 } ?: return null

      val dynamic = projectTable.safeGetArr<String>("dynamic").getOrIssue(issues)
      val version = projectTable.safeGet<String>("version").getOrIssue(issues) {
        if (dynamic?.contains("version") != true) {
          issues += PyProjectIssue.MissingVersion
        }
      }

      val requiresPython = projectTable.safeGet<String>("requires-python").getOrIssue(issues)
      val authors = projectTable.parseContacts("authors", issues)
      val maintainers = projectTable.parseContacts("maintainers", issues)
      val description = projectTable.safeGet<String>("description").getOrIssue(issues)
      val license = projectTable.safeGet<String>("license").getOrIssue(issues)
      val licenseFiles = projectTable.safeGetArr<String>("license-files").getOrIssue(issues)
      val keywords = projectTable.safeGetArr<String>("keywords").getOrIssue(issues)
      val classifiers = projectTable.safeGetArr<String>("classifiers").getOrIssue(issues)

      val readme = when (val res = projectTable.safeGet<String>("readme")) {
        is Result.Success -> {
          res.getOrIssue(issues)?.let { name ->
            PyProjectFile(name)
          }
        }
        is Result.Failure -> {
          val table = projectTable
            .safeGet<TomlTable>("readme")
            .getOrIssue(issues)

          val name = table
            ?.safeGetRequired<String>("name")
            ?.getOrIssue(issues)

          val contentType = table
            ?.safeGetRequired<String>("content-type")
            ?.getOrIssue(issues)

          if (name != null && contentType != null) {
            PyProjectFile(name, contentType)
          }
          else {
            null
          }
        }
      }

      val projectDependencies = projectTable.safeGetArr<String>(PY_PROJECT_DEPENDENCIES).getOrIssue(issues) ?: listOf()
      val depGroups = toml
        .safeGet<TomlTable>(PY_PROJECT_TOML_DEPENDENCY_GROUPS)
        .getOrIssue(issues)

      val depsFromGroups = depGroups?.keySet()?.associate { depGroupName ->
        // Can't filter by string because there might be (still unsupported) { include-group = "" } tables
        depGroupName to (depGroups.safeGetArr<Any>(depGroupName).getOrIssue(issues)?.filterIsInstance<String>() ?: emptyList())
      }
      val optionalDependencies =
        projectTable
          .safeGet<TomlTable>(PY_PROJECT_OPTIONAL_DEPENDENCIES)
          .getOrIssue(issues)
          ?.let { table ->
            mapOf(
              *table.keySet().mapNotNull { key ->
                table.safeGetArr<String>(key).getOrIssue(issues)?.let { value ->
                  key to value
                }
              }.toTypedArray()
            )
          }
        ?: mapOf()

      val scripts = projectTable.parseMap("scripts", issues)
      val guiScripts = projectTable.parseMap("gui-scripts", issues)
      val urls = projectTable.parseMap("urls", issues)

      return PyProjectToml(
        PyProjectTable(
          name,
          version,
          requiresPython,
          authors,
          maintainers,
          description,
          readme,
          license,
          licenseFiles,
          keywords,
          classifiers,
          dynamic,
          PyProjectDependencies(
            projectDependencies,
            optionalDependencies,
          ),
          scripts,
          guiScripts,
          urls,
        ),
        issues,
        toml,
        depGroupsToDeps = depsFromGroups ?: emptyMap(),
      )
    }

    /**
     * Attempts to find the `pyproject.toml` file in the provided module.
     * Returns null if not found.
     */
    suspend fun findPyProjectTomlFile(module: Module): PyProjectTomlFile? {
      return module.asPyProject()?.resolveFile(PY_PROJECT_TOML)
        ?.let { LocalFileSystem.getInstance().findFileByNioFile(it) }
        ?.let { PyProjectTomlFile(it) }
    }

    suspend fun findInRoot(moduleBasePath: Path): Path? = withContext(Dispatchers.IO) {
      moduleBasePath.resolve(PY_PROJECT_TOML).takeIf { it.isRegularFile() }
    }

    private fun TomlTable.parseContacts(
      key: String,
      issues: MutableList<PyProjectIssue>,
    ): List<PyProjectContact>? {
      val table = safeGetArr<TomlTable>(key).getOrIssue(issues) ?: return null
      return table.mapIndexedNotNull { index, authorTable ->
        val name = authorTable.safeGet<String>("name").getOrIssue(issues)
        val email = authorTable.safeGet<String>("email").getOrIssue(issues)

        if (name == null && email == null) {
          issues += PyProjectIssue.InvalidContact("$key[$index]")
          return@mapIndexedNotNull null
        }

        PyProjectContact(name, email)
      }
    }

    private fun TomlTable.parseMap(key: String, issues: MutableList<PyProjectIssue>): Map<String, String>? {
      val table = safeGet<TomlTable>(key).getOrIssue(issues) ?: return null
      return mapOf(
        *table.keySet().mapNotNull { key ->
          table.safeGet<String>(key).getOrIssue(issues)?.let { value ->
            key to value
          }
        }.toTypedArray()
      )
    }

    private fun <T> Result<T, TomlTableSafeGetError>.getOrIssue(issues: MutableList<PyProjectIssue>, onNull: (() -> Unit)? = null) =
      getOrIssue(issues, { PyProjectIssue.SafeGetError(it) }, onNull)
  }
}
