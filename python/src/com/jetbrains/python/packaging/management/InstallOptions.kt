// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.api.getPyProjectTomlFile
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pyproject.model.spi.PySdkDependencyGroupSupport
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.moduleIfExists

/**
 * Facade over [PyProjectManager.dependencyGroupSupport] for callers that only need the CLI flags
 * and don't want to resolve the manager themselves.
 *
 * A `null` [PySdkDependencyGroupSupport] means "this SDK does not model dependency groups" — the
 * empty-list result is the caller's signal to skip the group entirely. There is no universal
 * `--group` flag across Python package tools (pip, uv, Poetry all differ), so emitting a made-up
 * fallback would produce a bogus argument for unknown backends.
 */
internal suspend fun formatDependencyGroupArgs(
  sdk: Sdk,
  group: PyDependencyGroup,
  pyProjectToml: PyProjectToml?,
): List<String> = PyProjectManager.forSdk(sdk).dependencyGroupSupport
  ?.formatDependencyGroupArgs(sdk, group, pyProjectToml)
  .orEmpty()

/** `true` when the SDK's [PyProjectManager] declares group-aware support. */
internal fun isDependencyGroupSupported(sdk: Sdk): Boolean =
  PyProjectManager.forSdk(sdk).dependencyGroupSupport != null

internal data class PyPackageScope(
  val workspaceMember: PyWorkspaceMember? = null,
  val dependencyGroup: PyDependencyGroup? = null,
) {
  companion object {
    val NONE: PyPackageScope = PyPackageScope()
  }
}

/**
 * Structured install options for package installation dialogs.
 *
 * [toCliArgs] emits a neutral, pip-shaped wire format (`-e`, `--package`, `--group …`) that the
 * SDK low-level layer re-maps to the actual tool's flags (`uv add --editable`, Poetry's own group
 * syntax, …). Group flags are produced by the SDK-specific [PySdkDependencyGroupSupport]; the
 * editable and workspace-member flags live here because every currently-supported backend accepts
 * or transparently re-maps them.
 */
internal data class InstallOptions(
  val editable: Boolean = false,
  val workspaceMember: PyWorkspaceMember? = null,
  val dependencyGroup: PyDependencyGroup? = null,
) {
  suspend fun toCliArgs(sdk: Sdk, moduleOrProject: ModuleOrProject): List<String> = buildList {
    if (editable) add("-e")
    if (workspaceMember != null) {
      add("--package")
      add(workspaceMember.name)
    }
    if (dependencyGroup != null && dependencyGroup.name != "main") {
      val pyProjectToml = moduleOrProject.moduleIfExists?.getPyProjectTomlFile()?.let { vf ->
        PyProjectToml.parseCached(moduleOrProject.project, vf)
      }
      addAll(formatDependencyGroupArgs(sdk, dependencyGroup, pyProjectToml))
    }
  }
}
