package com.intellij.python.pyproject

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
data class PyDependencyGroup(
  val name: String,
  val kind: PyDependencyGroupKind = PyDependencyGroupKind.DEPENDENCY_GROUP,
)

/**
 * Where a dependency group lives in `pyproject.toml`. The two PEPs (621 and 735) put group-style
 * dependencies in different tables, and package managers expose different CLI flags for each:
 * uv for example uses `--optional` for PEP 621 and `--group` for PEP 735.
 */
@Internal
enum class PyDependencyGroupKind {
  /** PEP 735 — `[dependency-groups]` table. */
  DEPENDENCY_GROUP,

  /** PEP 621 — `[project.optional-dependencies]` table. */
  OPTIONAL_DEPENDENCY,
}