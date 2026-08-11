// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.dependencies

import com.intellij.python.pyproject.dependencies.spi.PyDependencyGroupLocator

private const val MAIN = "main"
private const val PROJECT = "project"
private const val DEPENDENCIES = "dependencies"
private const val DEPENDENCY_GROUPS = "dependency-groups"
private const val OPTIONAL_DEPENDENCIES = "optional-dependencies"
private val DEPENDENCY_GROUPS_PATH = listOf(DEPENDENCY_GROUPS)
private val PROJECT_PATH = listOf(PROJECT)
private val PROJECT_DEPENDENCIES_PATH = listOf(PROJECT, DEPENDENCIES)
private val PROJECT_OPTIONAL_DEPENDENCIES_PATH = listOf(PROJECT, OPTIONAL_DEPENDENCIES)

/**
 * PEP 621 (`[project]`) + PEP 735 (`[dependency-groups]`) resolver. Backs pip / uv / Hatch /
 * PDM `pyproject.toml` shapes.
 *
 * Matches:
 *  * the `dependencies` key inside a `[project]` table → `main`
 *  * `[project.dependencies]` header → `main`
 *  * `[project.optional-dependencies.<name>]` header → `<name>`
 *  * top-level keys of a `[project.optional-dependencies]` table → key name (PEP 621 inline form)
 *  * top-level keys of a `[dependency-groups]` table (PEP 735) → key name
 *
 * Does NOT match the bare `[project]` header — it is not a dependency group on its own. The
 * direct-parent check performed by the base dispatch avoids false positives for keys nested in
 * inline tables such as `{include-group = "test"}` (PEP 735 cross-references) which sit inside the
 * array value, not at the table top level.
 */
internal class PyProjectDependencyGroupLocator : PyDependencyGroupLocator {
  override fun resolveHeaderPath(path: List<String>): String? = when {
    // [project.dependencies] → the implicit "main" group (PEP 621 nested-header form).
    path == PROJECT_DEPENDENCIES_PATH -> MAIN
    // [project.optional-dependencies.<name>] → the "<name>" extra (PEP 621). Fixed prefix,
    // exactly three segments so nested `[project.optional-dependencies.<name>.foo]` doesn't match.
    path.size == 3 && path[0] == PROJECT && path[1] == OPTIONAL_DEPENDENCIES -> path[2]
    else -> null
  }

  override fun resolveInlineKey(ownerPath: List<String>, keyName: String): String? = when {
    // [dependency-groups]\n<name> = [...] (PEP 735).
    ownerPath == DEPENDENCY_GROUPS_PATH -> keyName
    // [project.optional-dependencies]\n<name> = [...] (PEP 621 inline form).
    ownerPath == PROJECT_OPTIONAL_DEPENDENCIES_PATH -> keyName
    // [project]\ndependencies = [...] (PEP 621 flat form) → the implicit "main" group.
    ownerPath == PROJECT_PATH && keyName == DEPENDENCIES -> MAIN
    else -> null
  }
}
