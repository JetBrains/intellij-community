// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.PySdkDependencyGroupSupport

/**
 * uv exposes a different CLI flag depending on where the dependency group lives in `pyproject.toml`:
 *  * PEP 621 `[project.optional-dependencies].<name>` → `--optional <name>`;
 *  * PEP 735 `[dependency-groups].<name>` → `--group <name>`.
 *
 * The caller resolves and parses `pyproject.toml` before invoking this method (see
 * [PySdkDependencyGroupSupport]). When the file is absent or the group is not listed under
 * `[project.optional-dependencies]` we fall back to `--group`, matching uv's own default for new
 * groups (it creates a `[dependency-groups]` entry in that case).
 */
internal object UvDependencyGroupSupport : PySdkDependencyGroupSupport {
  override suspend fun formatDependencyGroupArgs(
    sdk: Sdk,
    group: PyDependencyGroup,
    pyProjectToml: PyProjectToml?,
  ): List<String> {
    val isOptional = pyProjectToml?.project?.dependencies?.optional?.containsKey(group.name) == true
    val flag = if (isOptional) "--optional" else "--group"
    return listOf(flag, group.name)
  }
}
