// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

/**
 * Provides console filters for Python-related tools that use non-standard output formats.
 *
 * This provider adds support for:
 * - Ruff diagnostic format (cargo/clippy-style with arrow prefix)
 * - ty type checker diagnostic format (same as Ruff)
 *
 * @see AstralPathFilter
 */
internal class AstralConsoleFilterProvider : ConsoleFilterProvider {
  override fun getDefaultFilters(project: Project): Array<Filter> {
    val workingDirectory = project.basePath
    return arrayOf(AstralPathFilter(project, workingDirectory))
  }
}
