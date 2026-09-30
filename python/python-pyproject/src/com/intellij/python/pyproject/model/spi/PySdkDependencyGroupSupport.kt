// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.spi

import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.PyProjectToml
import org.jetbrains.annotations.ApiStatus

/**
 * Dependency-group CLI adapter attached to a [PyProjectManager]. Presence of a non-null
 * [PyProjectManager.dependencyGroupSupport] marks the interpreter as group-aware; the same object
 * produces the CLI flags for a chosen [PyDependencyGroup].
 *
 * Kept as a nested capability of [PyProjectManager] (rather than a separate extension point)
 * because both are keyed off the interpreter flavor and having two parallel EPs meant every
 * project manager had to be registered twice.
 */
@ApiStatus.Internal
interface PySdkDependencyGroupSupport {
  /**
   * CLI flags for [group] under [interpreter].
   *
   * [pyProjectToml] is the caller-resolved, pre-parsed `pyproject.toml` for the active module,
   * or `null` when no module is associated or the file cannot be found. Implementations that
   * need to distinguish `[project.optional-dependencies]` from `[dependency-groups]` (e.g. uv)
   * inspect it here; implementations that emit a fixed flag regardless (e.g. Poetry) may ignore
   * it.
   */
  suspend fun formatDependencyGroupArgs(
    interpreter: PythonInterpreter,
    group: PyDependencyGroup,
    pyProjectToml: PyProjectToml?,
  ): List<String>
}
