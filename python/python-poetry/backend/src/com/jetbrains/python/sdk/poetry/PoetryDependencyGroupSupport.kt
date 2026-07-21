// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.PySdkDependencyGroupSupport

/** Poetry uses `--group <name>` regardless of the toml section, so no [pyProjectToml] lookup. */
internal object PoetryDependencyGroupSupport : PySdkDependencyGroupSupport {
  override suspend fun formatDependencyGroupArgs(
      sdk: Sdk,
      group: PyDependencyGroup,
      pyProjectToml: PyProjectToml?,
  ): List<String> = listOf("--group", group.name)
}