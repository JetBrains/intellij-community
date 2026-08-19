// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject

import com.jetbrains.python.Result
import com.jetbrains.python.packaging.PyPackageName
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

/**
 * Represents a parsed `pyproject.toml` file.
 * Any inconsistencies with the spec and the parsed values are represented by the [PyProjectToml.issues] list after parsing.
 *
 * @see [pyproject.toml specification](https://packaging.python.org/en/latest/specifications/pyproject-toml/)
 *
 * Create via [make] or [makeVirtProj]
 */
@Internal
@ExposedCopyVisibility
data class PyProjectTable @VisibleForTesting internal constructor(
  val name: String,
  val version: String? = null,
  val requiresPython: String? = null,
  val authors: List<PyProjectContact>? = null,
  val maintainers: List<PyProjectContact>? = null,
  val description: String? = null,
  val readme: PyProjectFile? = null,
  val license: String? = null,
  val licenseFiles: List<String>? = null,
  val keywords: List<String>? = null,
  val classifiers: List<String>? = null,
  val dynamic: List<String>? = null,
  val dependencies: PyProjectDependencies = PyProjectDependencies(),
  val scripts: Map<String, String>? = null,
  val guiScripts: Map<String, String>? = null,
  val urls: Map<String, String>? = null,
) {
  internal companion object {
    /**
     * Virtual project has no `[project]` table, but has [name]
     */
    internal fun makeVirtProj(projectName: String): PyProjectTable = PyProjectTable(name = PyPackageName.normalizeProjectName(projectName))

    /**
     * from [projectTable] (which is `[project]` table) fetch all data, report issues to [issues] and return instance if valid.
     */
    internal fun make(projectTable: TomlTable, issues: MutableList<PyProjectIssue>): PyProjectTable? {
      val name = projectTable.safeGetRequired<String>("name").getOrIssue(issues) ?: return null

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



      return PyProjectTable(
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
      )
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
  }
}


