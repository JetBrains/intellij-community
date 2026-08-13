// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.statistics.PyToolFusSnapshot
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import com.intellij.openapi.util.Version as PlatformVersion

interface PyTool : PyExecutable {
  val presentableName: @NlsSafe String
  val packageName: PyPackageName

  /** Icon representing the tool (e.g. status-bar widget, advertiser notification, External Tools table). */
  val icon: Icon

  /**
   * One-line user-facing description of the tool (e.g. "Linter and code formatter for Python").
   * Surfaced in the External Tools settings tooltip. Required — every tool must provide a
   * localized message from its own resource bundle.
   */
  val description: @Nls String

  /**
   * Provides a unique identifier (python package name) for the feature usage statistics (FUS) system.
   * The identifier is dynamically derived from the first package name in the list of known package names.
   **
   * @return A string representing the FUS identifier for the tool, validated via the dictionary of well-known python package names.
   */
  override val fusId: String get() = packageName.name

  /**
   * Every executable this tool provides: the tool's own command plus any secondary entry points that
   * ship with it (e.g. uv also provides `uvx`, pyright also provides `pyright-langserver`). Each has its
   * own custom-path / detection-cache entry keyed by [PyExecutable.fusId]. Defaults to just the tool
   * itself; tools with extra runners override. Resolve a bare command name through [findExecutable].
   */
  val executables: List<PyExecutable> get() = listOf(this)

  /**
   * How this tool installs/upgrades itself from the settings UI. Defaults to [PackagePyToolManager]
   * (install as a Python package via uv/pip). Tools installed a different way (conda) override; `null`
   * means the tool can't be installed through the IDE — the row only lets the user set its path.
   */
  val manager: PyToolManager? get() = PackagePyToolManager

  /**
   * Lowest tool version the IDE integration is known to work with, or `null` if there is no such
   * floor. The External Tools UI surfaces this both as a hint on the per-tool detail panel and as
   * an inline warning on the table when the resolved binary reports an older version. Tools that
   * have no version requirement (e.g. the integration accepts whatever the user has installed) can
   * leave the default.
   */
  val minimumSupportedVersion: PlatformVersion? get() = null

  /**
   * One-time migration from this tool's pre-[PyToolsState] configuration. Called once per project by
   * [PyToolsState] when it has no stored state yet. Implementations read their old settings, **reset those
   * old settings to their defaults** (so the migration is one-way and re-running it can never resurrect the
   * old values), and return the equivalent [PyToolsState.ToolEntry] — or `null` if there is nothing to migrate.
   */
  fun migrateLegacyState(project: Project): PyToolsState.ToolEntry? = null

  /**
   * Compact, comma-separated summary of currently-activated features for the External Tools table
   * (e.g. "Inspections, Formatting"). Returning an empty string hides the cell content for the
   * tool, which is appropriate when the tool has no per-feature toggles.
   */
  fun summaryFor(project: Project): @NlsSafe String = ""

  /** Invoked on Apply when the table flips this tool's enabled state. Tools start/stop their LSP servers here. */
  fun onEnabledChanged(project: Project, enabled: Boolean) {}

  /**
   * Whether this tool is currently the project's selected type engine. Tools that can double as an
   * external type engine (Pyrefly, ty) override this; ordinary LSP tools keep the default. When it
   * is `true` the External Tools UI locks this tool's enable toggle (it is governed by the Type
   * Engine settings instead), and [isActiveOn] reports the tool as active so the shared LSP server
   * and its features stay on while the tool acts as the engine.
   */
  fun isSelectedAsTypeEngine(project: Project): Boolean = false

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
      customPath = getCustomExecutablePath(project.getEelDescriptor()) != null,
    )
  }

  companion object {
    val EP_NAME: ExtensionPointName<PyTool> = ExtensionPointName.create("com.intellij.python.pytools.pyTool")

    fun findByPackageName(packageName: String): PyTool? {
      val normalized = PyPackageName.from(packageName).name
      return EP_NAME.extensionList.firstOrNull { it.packageName.name == normalized }
    }

    /**
     * The [PyExecutable] whose command name is [name], searched across every registered tool's
     * [executables] (own command and secondary entry points), or `null` if no tool provides it.
     */
    fun findExecutable(name: String): PyExecutable? =
      EP_NAME.extensionList.firstNotNullOfOrNull { tool -> tool.executables.firstOrNull { it.fusId == name } }
  }
}

/**
 * Marks a [PyTool] as one that is **listed on the External Tools settings page**. Presence of this
 * interface is what makes a tool appear (and be searchable) there. It also contributes the tool's detail
 * panel — the feature toggles shown inline when the tool's row is expanded. Kept separate from [PyTool]
 * so a tool opts into the page without every tool having to.
 */
interface ExternalPyTool {
  /** The inline detail configurable (feature toggles) embedded in the tool's expanded row. */
  fun createConfigurable(project: Project): UnnamedConfigurable
}

/**
 * Marks a [PyTool] as one that is **listed on the Package Managers settings page** (uv, Poetry, Hatch,
 * Pipenv, conda). Presence of this interface is what makes a tool appear (and be searchable) there.
 *
 * A package manager has no per-tool feature panel; the page only shows and edits the executable path.
 * The custom path itself is a common [PyTool] concern, stored per Eel machine via
 * [getCustomExecutablePath] / [setCustomExecutablePath] — the same mechanism the External Tools page
 * uses — so this interface carries no path state of its own.
 */
interface PackageManagerPyTool
