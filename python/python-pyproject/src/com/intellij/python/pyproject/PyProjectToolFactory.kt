// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject

import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * A factory for `pyproject.toml` tool parsers.
 *
 * Example:
 *
 * ```kotlin
 * data class UvPyProject(val dependencies: List<String>) {
 *   companion object : PyProjectToolFactory<UvPyProject> {
 *     override val tables: List<String> = listOf("tool.uv")
 *
 *     override fun createTool(tables: Map<String, TomlTable?>): UvPyProject {
 *       val dependencies = tables["tool.uv"]?.safeGetArr<String>("dev-dependencies")?.successOrNull ?: listOf()
 *       return UvPyProject(uvDevDependencies)
 *     }
 *   }
 * }
 * ```
 */
@Internal
interface PyProjectToolFactory<T> {
  /**
   * A list of strings that represent [TomlTable]s to be provided by [PyProjectToml.getTool] to [createTool]'s map.
   * If a table is absent from the file, the corresponding value in the map will be null.
   */
  val tables: List<String>

  /**
   * Constructs a concrete tool [T].
   */
  fun createTool(tables: Map<String, TomlTable?>): T
}