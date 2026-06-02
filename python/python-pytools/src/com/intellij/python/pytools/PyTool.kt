// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.statistics.PyToolFusSnapshot
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.Nls
import com.intellij.openapi.util.Version as PlatformVersion

data class InstallInfo(
  val packageName: PyPackageName,
  val installHelp: @Nls String? = null,
)

interface PyTool {
  val presentableName: @NlsSafe String
  val packageName: PyPackageName
  val aliases: List<PyPackageName> get() = listOf(packageName)

  /**
   * One-line user-facing description of the tool (e.g. "Linter and code formatter for Python").
   * Surfaced in the External Tools settings tooltip. Required — every tool must provide a
   * localized message from its own resource bundle.
   */
  val description: @Nls String

  val installInfo: InstallInfo get() = InstallInfo(packageName)

  /**
   * Provides a unique identifier (python package name) for the feature usage statistics (FUS) system.
   * The identifier is dynamically derived from the first package name in the list of known package names.
   **
   * @return A string representing the FUS identifier for the tool, validated via the dictionary of well-known python package names.
   */
  val fusId: String get() = packageName.name

  /**
   * Lowest tool version the IDE integration is known to work with, or `null` if there is no such
   * floor. The External Tools UI surfaces this both as a hint on the per-tool detail panel and as
   * an inline warning on the table when the resolved binary reports an older version. Tools that
   * have no version requirement (e.g. the integration accepts whatever the user has installed) can
   * leave the default.
   */
  val minimumSupportedVersion: PlatformVersion? get() = null

  /** Pre-migration `enabled` value, used by [PyToolsState] on first read. */
  fun legacyEnabled(project: Project): Boolean = false

  /** Pre-migration discovery mode, used by [PyToolsState] on first read. */
  fun legacyDiscoveryMode(project: Project): ExecutableDiscoveryMode = ExecutableDiscoveryMode.INTERPRETER

  /** Pre-migration custom path, used by [PyToolsState] on first read. */
  fun legacyCustomPath(project: Project): java.nio.file.Path? = null

  /**
   * Factory that builds the per-tool detail UI shown in the Edit dialog of the External Tools
   * table. `null` (the default) means the tool has no extra options — the table uses this to dim
   * and disable the gear icon for the row.
   */
  val detailConfigurable: ((Project) -> UnnamedConfigurable)? get() = null

  /**
   * Compact, comma-separated summary of currently-activated features for the External Tools table
   * (e.g. "Inspections, Formatting"). Returning an empty string hides the cell content for the
   * tool, which is appropriate when the tool has no per-feature toggles.
   */
  fun summaryFor(project: Project): @NlsSafe String = ""

  /** Invoked on Apply when the table flips this tool's enabled state. Tools start/stop their LSP servers here. */
  fun onEnabledChanged(project: Project, enabled: Boolean) {}

  /**
   * Snapshot every configuration field this tool owns, for FUS logging. The default returns
   * just `enabled` + `executableDiscoveryMode` (read from [PyToolsState]); tools with extra
   * settings (e.g. LSP feature flags) override and `copy(...)` the result to add them.
   *
   * Called from a single emit point in `PyToolUsagesCollector.Helper.logConfigurationChanged`,
   * which means a new tool that adds settings without overriding this method will still log a
   * usable enabled/mode event — no silent FUS gap.
   */
  fun configurationFusSnapshot(project: Project): PyToolFusSnapshot {
    val entry = PyToolsState.getInstance(project).getEntry(this)
    return PyToolFusSnapshot(
      enabled = entry.enabled,
      executableDiscoveryMode = entry.discoveryMode,
      customPath = entry.customToolBinaryPath != null,
    )
  }

  companion object {
    val EP_NAME: ExtensionPointName<PyTool> = ExtensionPointName.create("com.intellij.python.pytools.pyTool")
  }
}
