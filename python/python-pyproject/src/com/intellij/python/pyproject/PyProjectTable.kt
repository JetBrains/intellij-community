// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Represents a parsed `pyproject.toml` file.
 * Any inconsistencies with the spec and the parsed values are represented by the [PyProjectToml.issues] list after parsing.
 *
 * @see [pyproject.toml specification](https://packaging.python.org/en/latest/specifications/pyproject-toml/)
 */
@Internal
data class PyProjectTable(
  val name: String? = null,
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
)

/**
 * Represents the dependencies of the project.
 */
@Internal
data class PyProjectDependencies(
  /**
   * Dependencies provided in `project.dependencies`.
   */
  val project: List<String> = listOf(),

  /**
   * Dependencies provided in `dependency-groups.dev`.
   */
  val dev: List<String> = listOf(),

  /**
   * Dependencies provided in `project.optional-dependencies`.
   */
  val optional: Map<String, List<String>> = mapOf(),
)

/**
 * Represents a file object.
 */
@Internal
data class PyProjectFile(
  val name: String,
  val contentType: String? = null,
)

/**
 * Represents a contact. Both [name] and [email] can't be absent at the same time.
 */
@Internal
data class PyProjectContact(val name: String?, val email: String?) {
  init {
    if (name == null && email == null) {
      throw IllegalArgumentException("at least name or email should be provided")
    }
  }
}