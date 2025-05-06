// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.Result
import com.jetbrains.python.Result.Companion.success
import com.jetbrains.python.sdk.findAmongRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlParseError
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.InputStream

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
   * Signifies that the name is missing from the `project` section.
   */
  data object MissingName : PyProjectIssue()

  /**
   * Signifies that the version is missing from the `project` section, while also being absent from the `dynamic` array.
   */
  data object MissingVersion : PyProjectIssue()

  /**
   * Wraps [TomlTableSafeGetError].
   */
  data class SafeGetError(val error: TomlTableSafeGetError) : PyProjectIssue()

  /**
   * Signifies that a contact misses both `name` and `email` fields.
   */
  data class InvalidContact(val path: String) : PyProjectIssue()
}

/**
 * A general handler for `pyproject.toml` files.
 */
@Internal
data class PyProjectToml(
  /**
   * Represents the parsed `pyproject.toml` file.
   * This field can be null when the `project` section is missing.
   */
  val project: PyProjectTable?,

  /**
   * A list of issues that occurred during the execution of [PyProjectToml.parse].
   */
  val issues: List<PyProjectIssue>,

  /**
   * An instance of [TomlTable] provided by the TOML parser.
   */
  val toml: TomlTable,
) {
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

  companion object {
    /**
     * Attempts to parse [inputStream] and construct an instance of [PyProjectToml].
     * On success, returns an instance of [Result.Success] with an instance of [PyProjectToml].
     * On failure, returns an instance of [Result.Failure] with a list of [TomlParseError]s.
     *
     * Example:
     *
     * ```kotlin
     * val pyProject = PyProjectToml.parse(psiFile.virtualFile.inputStream).orThrow()
     * val uvTool = pyProject.getTool(UvPyProject)
     * val hatch = pyProject.getTool(HatchPyProject)
     * ```
     */
    fun parse(inputStream: InputStream): Result<PyProjectToml, List<TomlParseError>> {
      val issues = mutableListOf<PyProjectIssue>()
      val toml = Toml.parse(inputStream)

      if (toml.hasErrors()) {
        return Result.failure(toml.errors())
      }

      val projectTable = toml.safeGet<TomlTable>("project").getOrIssue(issues)

      if (projectTable == null) {
        return success(PyProjectToml(null, issues, toml))
      }

      val name = projectTable.safeGet<String>("name").getOrIssue(issues) {
        issues += PyProjectIssue.MissingName
      }

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

      val projectDependencies = projectTable.safeGetArr<String>("dependencies").getOrIssue(issues) ?: listOf()
      val devDependencies =
        toml
          .safeGet<TomlTable>("dependency-groups")
          .getOrIssue(issues)
          ?.safeGetArr<String>("dev")
          ?.getOrIssue(issues)
          ?.toList()
        ?: listOf()
      val optionalDependencies =
        projectTable
          .safeGet<TomlTable>("optional-dependencies")
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

      return success(
        PyProjectToml(
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
              devDependencies,
              optionalDependencies
            ),
            scripts,
            guiScripts,
            urls,
          ),
          issues,
          toml,
        )
      )
    }

    /**
     * Attempts to find the `pyproject.toml` file in the provided module.
     * Returns null if not found.
     */
    suspend fun findFile(module: Module): VirtualFile? =
      withContext(Dispatchers.IO) {
        findAmongRoots(module, PY_PROJECT_TOML)
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
