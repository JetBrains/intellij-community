// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.backend.statistics.PyToolFusSnapshot
import com.intellij.python.pytools.common.PyToolConfigurationDto
import com.jetbrains.python.packaging.PyPackageName

/**
 * A [PyTool] with project-level state: a formatter, a linter or a type checker the user adopts into one
 * project. Black and the LSP tools are the ones on the External Tools settings page today.
 *
 * Project-level as opposed to the per-Eel-machine state the [PyTool] it extends carries: where a tool resolves
 * is keyed by machine, while whether the user turned it on and how they configured it belong to one project.
 * The two are independent — one machine's binary serves every project on it, and each of those projects
 * enables and configures it on its own. So everything taking a [Project] lives here: the enabled flag
 * ([isEnabledOn], [setEnabledOn]), the configuration of type [C], the type-engine selection, and the hooks
 * that fire when the enablement or the executable changes. A [PackageManagerPyTool] has none of it, which is
 * why [PyTool] itself takes no [Project] at all.
 *
 * `ProjectLevelPyToolFrontend` is the frontend half, implemented by the same tools; the two talk over
 * [ProjectLevelPyToolApi]. This extends [PyTool] rather than standing alone because that API addresses a tool
 * by its id and resolves it through the [PyTool] extension point: only a registered tool can be one of these,
 * and inheriting states that in the type system and gives every call site the id for free.
 */
interface ProjectLevelPyTool<C : PyToolConfigurationDto> : PyTool {
  /**
   * Migrates this tool's pre-[PyToolsState] settings, called once per project when the store is empty.
   *
   * The implementation must clear what it read, which makes the migration one-way: re-running it can never
   * resurrect the old values. Only the enabled flag survives into [PyToolsState.ToolEntry] — a legacy custom
   * path is dropped rather than migrated, because paths now live per Eel machine in `PyCustomExecutablePaths`
   * and importing a stale per-project one could clobber a path the user already set. A tool with nothing to
   * migrate returns a plain [PyToolsState.ToolEntry], which persists as the default.
   */
  fun migrateLegacyState(project: Project): PyToolsState.ToolEntry

  /**
   * Reacts to an applied change of the enabled flag, and only to an applied one: [setEnabledOn] is the single
   * path that writes the flag and calls this, so the two never drift apart.
   *
   * An LSP tool starts or stops its server here. Nothing else does, so a tool that skips this stays enabled
   * on paper and not running.
   */
  fun onEnabledChanged(project: Project, enabled: Boolean) {}

  /**
   * Returns true when the project uses this tool as its type engine.
   *
   * A selected type engine counts as active even with its enabled flag off, which is what [isActiveOn]
   * captures for an LSP tool. Declared here and not in the LSP module, where the only type engines live,
   * because [buildToolState] reports it to the settings page and `python-pytools` cannot depend on that
   * module; tools that are not type engines leave the default.
   */
  fun isSelectedAsTypeEngine(project: Project): Boolean = false

  /**
   * Invoked when the executable this tool would run may have changed. Any persisting is already done
   * and the detection cache already dropped, so an implementation resolving the tool here sees the
   * new binary.
   *
   * These changes invoke it:
   * - An edit of the custom path, and an install or an upgrade through [manager]. The path store, the
   *   detection cache and such an install are application-level and keyed by Eel machine. So each of
   *   these changes reaches every open [project] that resolves this tool on that machine. See
   *   [notifyExecutableChanged].
   * - For an LSP tool, a new SDK of a module, and a new installed version of the tool in an SDK. The
   *   Python LSP integration sends these, and each reaches only the [project] it belongs to. It
   *   merges a burst of these changes into one call.
   *
   * A tool that runs a server implements this, because the server it started is still the old binary
   * and nothing else restarts it. So does a tool that caches anything derived from its binary, because
   * the new binary can answer differently.
   */
  fun onExecutableChanged(project: Project) {}

  /**
   * Returns everything this tool reports about its configuration for feature usage statistics.
   *
   * The default covers what every external tool has: the enabled flag and whether the user set a custom path.
   * A tool with feature toggles of its own adds them by copying the default, as the LSP tools do.
   */
  fun configurationFusSnapshot(project: Project): PyToolFusSnapshot = PyToolFusSnapshot(
    enabled = PyToolsState.getInstance(project).isEnabled(this),
    customPath = getCustomExecutablePath(project.getEelDescriptor()) != null,
  )

  /**
   * Reads the tool's settings into the DTO the frontend edits.
   *
   * This is a projection, not the settings themselves: the backing store is a `PersistentStateComponent`
   * that also holds deprecated fields the frontend must never see or write.
   */
  fun configurationState(project: Project): C

  /**
   * Writes back a configuration the user edited on the External Tools page.
   *
   * Reached only through [ProjectLevelPyToolApi], which drops a DTO whose type this tool did not declare, so
   * an implementation can trust [state] to be its own.
   */
  fun applyConfigurationState(project: Project, state: C)

  companion object {
    /**
     * Every registered external tool.
     *
     * A sequence read through on each call, because the [PyTool] extension list is dynamic: a plugin that
     * loads or unloads changes which tools are there.
     */
    val extensions: Sequence<ProjectLevelPyTool<*>>
      get() = PyTool.EP_NAME.extensionList.asSequence().filterIsInstance<ProjectLevelPyTool<*>>()

    /**
     * The external tool that this Python package installs, or `null` when the package names a tool with no
     * per-project state (a package manager) or no tool at all.
     */
    fun findByPackageName(packageName: String): ProjectLevelPyTool<*>? {
      val normalized = PyPackageName.from(packageName).name
      return extensions.firstOrNull { it.packageName.name == normalized }
    }
  }
}

/**
 * Applies the enabled flag and runs [ProjectLevelPyTool.onEnabledChanged].
 *
 * Always both. The hook is what starts or stops the tool's LSP server, so writing [PyToolsState] on its own
 * leaves a tool enabled on paper and not running — which is why enabling never goes through
 * [PyToolsState.setEnabled] directly.
 */
fun ProjectLevelPyTool<*>.setEnabledOn(project: Project, enabled: Boolean) {
  PyToolsState.getInstance(project).setEnabled(this, enabled)
  onEnabledChanged(project, enabled)
}

/**
 * Returns true when the user enabled this tool for the project.
 *
 * An LSP tool asks [isActiveOn] instead, which also counts being the selected type engine.
 */
fun ProjectLevelPyTool<*>.isEnabledOn(project: Project): Boolean =
  PyToolsState.getInstance(project).isEnabled(this)
