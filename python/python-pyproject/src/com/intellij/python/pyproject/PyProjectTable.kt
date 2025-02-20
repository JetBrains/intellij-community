// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject

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

data class PyProjectDependencies(
  val project: List<String> = listOf(),
  val dev: List<String> = listOf(),
  val optional: Map<String, List<String>> = mapOf(),
)

data class PyProjectFile(
  val name: String,
  val contentType: String? = null,
)

data class PyProjectContact(val name: String?, val email: String?) {
  init {
    if (name == null && email == null) {
      throw IllegalArgumentException("at least name or email should be provided")
    }
  }
}