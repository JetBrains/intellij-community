// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.pytools.statistics.PyToolUsagesCollector
import com.intellij.python.pytools.statistics.PyToolActionSource
import com.intellij.python.pytools.Version
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.backend.setUvExecutableLocal
import com.intellij.python.pytools.PyToolManager
import com.intellij.python.pytools.PyToolManagerProvider
import com.intellij.python.pytools.performToolInstallation
import com.intellij.python.pytools.performToolUpgrade
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.sdk.uv.impl.hasUvExecutableLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the External Tools page's tool actions: the install / upgrade actions invoked from the Path
 * column's hover icon (delegated to [PyToolManager] — uv when present, pip otherwise), the aggregated
 * set of outdated tools that drives the upgrade affordance, uv-availability detection, and the
 * install-uv-itself action invoked from the footer hint.
 *
 * The configurable hands the controller a [CoroutineScope] via [onShown] — typically the scope
 * provided by `JComponent.launchOnShow`, so detection and background tasks live for the panel's
 * showing-lifetime and are cancelled automatically when the page is hidden. Two callbacks bridge
 * back to the configurable on EDT:
 *  - [onStateChanged] whenever state changes (uv availability / the outdated-tool set) so the
 *    configurable can refresh the table and rebuild the footer hint;
 *  - [refreshRow] forwarded into [ToolRow.probeVersion] after a successful install/upgrade so
 *    the freshly resolved version flows back into the row.
 */
internal class PyToolManagementController(
  private val project: Project,
  private val onStateChanged: () -> Unit,
  private val refreshRow: (ToolRow) -> Unit,
) {
  /**
   * The active showing-scope, set by [onShown] and replaced on each hide/show cycle. Click-driven
   * actions launch into this scope; if the scope is `null` (the panel hasn't been shown yet) those
   * actions skip cleanly.
   */
  private var scope: CoroutineScope? = null
  /**
   * Whether the local `uv` executable is available. Detected once asynchronously after the page
   * opens (the call is suspending so it can't run from the renderer); the install icon in the
   * Path column is gated on this. Three states:
   *  - `null` — initial detection hasn't completed yet
   *  - `false` — detection done, uv is not installed (drives the `UvHintFooter` visibility)
   *  - `true` — uv is installed
   *
   * Exposed as an [AtomicProperty] so UI components (the hint footer's row) can bind their
   * visibility to it via `Row.visibleIf` instead of imperatively refreshing.
   */
  val uvAvailable: AtomicProperty<Boolean?> = AtomicProperty(null)

  /**
   * Package name → latest available version for tools that have a newer release, aggregated across all
   * registered [PyToolManager]s (so it is populated even without uv). Drives the upgrade icon and its
   * tooltip. Refreshed asynchronously; a tool present here is, by definition, manageable and outdated.
   */
  @Volatile
  var outdatedVersions: Map<String, String> = emptyMap()
    private set

  /**
   * Package name this row's tool is known by. Each tool maps 1:1 to a single PyPI package, so every
   * install / upgrade / outdated lookup targets the tool's package name.
   */
  private fun ToolRow.uvPackageName(): String = tool.packageName.name

  /** Whether the upgrade icon should be offered for [toolRow] — i.e. a newer version is known. */
  fun isUpgradeAvailable(toolRow: ToolRow): Boolean = toolRow.uvPackageName() in outdatedVersions

  /** Latest version [toolRow]'s tool can be upgraded to, when known. */
  fun latestVersionFor(toolRow: ToolRow): String? = outdatedVersions[toolRow.uvPackageName()]

  /**
   * Called by the configurable from within `launchOnShow`'s coroutine. Stores [scope] for
   * click-driven actions, detects uv availability (for the footer/uvx), and loads outdated tools.
   */
  fun onShown(scope: CoroutineScope) {
    this.scope = scope
    scope.launch {
      uvAvailable.set(hasUvExecutableLocal())
      withContext(Dispatchers.Main) { onStateChanged() }
      refreshOutdated()
    }
  }

  /** Install the row's tool with modal progress via the shared installer path; refresh on success. */
  fun installTool(toolRow: ToolRow, source: PyToolActionSource) = runToolAction(
    toolRow = toolRow,
    progressTitleKey = "settings.external.tools.install.progress",
    errorTitleKey = "settings.external.tools.install.error.title",
    action = { toolRow.tool.performToolInstallation(project.getEelDescriptor().toEelApi()) },
    onSuccess = {
      PyToolUsagesCollector.Helper.logToolInstalled(project, toolRow.tool, source)
      // Baseline copy — surfaced while the post-action `--version` probe is still running and
      // we don't yet know the freshly installed version.
      toolRow.lastSuccessMessage =
        PyToolsUiBundle.message("settings.external.tools.install.success.balloon", toolRow.uvPackageName())
    },
    onVersionResolved = { installedVersion ->
      // [runUvToolAction] only fires this hook when the probed version is non-null, so we can
      // safely format it into the version-bearing message; otherwise the baseline copy from
      // [onSuccess] stays.
      if (installedVersion != null) {
        toolRow.lastSuccessMessage = PyToolsUiBundle.message(
          "settings.external.tools.install.success.to.version.balloon",
          toolRow.uvPackageName(), installedVersion,
        )
        refreshRow(toolRow)
      }
    },
  )

  /**
   * Bring [toolRow]'s tool up to the latest version via `uv tool install --reinstall`. We
   * deliberately avoid `uv tool upgrade`: it respects the spec the tool was originally installed
   * with, so a pinned `name==X` install never moves off X. `install --reinstall` drops the prior
   * pin and resolves to the latest compatible release, which matches what the user expects when
   * they click the "Upgrade" icon next to an outdated tool.
   */
  fun upgradeTool(toolRow: ToolRow, source: PyToolActionSource) {
    // Snapshot the pre-upgrade version so we can tell, after the post-action re-probe, whether
    // the install actually changed anything (e.g. it could have already been latest).
    val previousVersion = toolRow.version
    runToolAction(
      toolRow = toolRow,
      progressTitleKey = "settings.external.tools.upgrade.progress",
      errorTitleKey = "settings.external.tools.upgrade.error.title",
      action = { toolRow.tool.performToolUpgrade(project.getEelDescriptor().toEelApi()) },
      onSuccess = {
        PyToolUsagesCollector.Helper.logToolUpdated(project, toolRow.tool, source)
      },
      onVersionResolved = { newVersion ->
        // Refine the baseline success message now that the post-action version is known
        // (Upgraded to X / already up to date / version-less fallback).
        toolRow.lastSuccessMessage = upgradeFeedbackMessage(toolRow, previousVersion, newVersion)
        refreshRow(toolRow)
      },
    )
  }

  /**
   * Choose the feedback message for a completed `uv tool upgrade`. Three branches:
   *  - same version before and after → "X is already up to date (vN)" — uv ran, nothing changed;
   *  - different version (or no prior version) and we have a new one → "X upgraded to vN";
   *  - no new version (probe failed or returned null) → version-less "X upgraded" fallback so
   *    we don't leave the user with no confirmation at all.
   */
  private fun upgradeFeedbackMessage(toolRow: ToolRow, previous: Version?, current: Version?): String {
    // Match the progress dialog and `uv tool list`: show the package name uv actually touched
    // (e.g. "basedpyright"), not the tool's IDE-facing label ("Pyright").
    val name = toolRow.uvPackageName()
    return when {
      current != null && previous != null && previous == current ->
        PyToolsUiBundle.message("settings.external.tools.upgrade.up.to.date.balloon", name, current)
      current != null ->
        PyToolsUiBundle.message("settings.external.tools.upgrade.success.to.version.balloon", name, current)
      else ->
        PyToolsUiBundle.message("settings.external.tools.upgrade.success.balloon", name)
    }
  }

  /**
   * Install `uv` itself through [UvPyTool]'s `performToolInstallation` (uv can't install itself, so
   * this falls through to the pip-based installer into a system Python). On success, persists the path
   * with `setUvExecutableLocal` (matching the SDK setup flow), flips [uvAvailable] on, and fires
   * [onStateChanged] so the configurable can rebuild the footer hint and the table.
   */
  fun installUv() {
    val title = PyToolsUiBundle.message("settings.external.tools.install.uv.progress")
    val errorTitle = PyToolsUiBundle.message("settings.external.tools.install.uv.error.title")
    val result = runWithModalProgressBlocking(project, title) {
      UvPyTool.getInstance().performToolInstallation(project.getEelDescriptor().toEelApi())
    }
    val installedPath = when (result) {
      is Result.Success -> result.result
      is Result.Failure -> {
        Messages.showErrorDialog(project, result.error.toString(), errorTitle)
        return
      }
    }
    setUvExecutableLocal(installedPath)
    uvAvailable.set(true)
    onStateChanged()
    // Now that uv exists, reload the outdated set so upgrade affordances light up.
    scope?.launch { refreshOutdated() }
  }

  /**
   * Shared driver for a tool install / upgrade. Runs [action] under modal progress, surfaces errors
   * via a message dialog, and on success invalidates the row's cached probe and refreshes the
   * outdated-tool set (so an Install can light up an upgrade affordance afterwards).
   */
  private fun runToolAction(
    toolRow: ToolRow,
    progressTitleKey: String,
    errorTitleKey: String,
    action: suspend () -> PyResult<*>,
    onSuccess: () -> Unit = {},
    /**
     * Fires once after the post-action `--version` re-probe publishes a version (or skips if
     * the probe never produces one before the row is reset). The argument is the freshly probed
     * version, suitable for comparing against a caller-captured snapshot.
     */
    onVersionResolved: (Version?) -> Unit = {},
  ) {
    // The package name the operation targets — used for the progress/error titles and, later, the
    // post-action `--version` re-probe. Showing e.g. "basedpyright" (what uv actually installs) keeps
    // the dialog consistent with `uv tool list` even when the row's label differs ("Pyright").
    val packageName = toolRow.uvPackageName()
    // The progress and error titles surface the same `packageName`: showing "Installing
    // basedpyright" instead of the tool's presentable name keeps the dialog consistent with
    // what the user will later see in `uv tool list` (and explains the divergence — clicking
    // "Pyright" actually installs `basedpyright`).
    val title = PyToolsUiBundle.message(progressTitleKey, packageName)
    val errorTitle = PyToolsUiBundle.message(errorTitleKey, packageName)
    // Keep the spinner up across the entire lifecycle: click → modal → post-action version
    // re-probe. Clearing earlier (e.g. the moment the modal closes) lets the row briefly fall
    // back to the regular icon while the new `--version` is still being fetched, producing a
    // visible gap before the ✓ appears.
    toolRow.actionInProgress = true
    refreshRow(toolRow)
    val result = try {
      runWithModalProgressBlocking(project, title) { action() }
    }
    catch (e: CancellationException) {
      toolRow.actionInProgress = false
      refreshRow(toolRow)
      throw e
    }
    val failure = result as? Result.Failure<*>
    if (failure != null) {
      toolRow.actionInProgress = false
      refreshRow(toolRow)
      Messages.showErrorDialog(project, failure.error.toString(), errorTitle)
      return
    }
    onSuccess()
    // Invalidate the cached probe so the freshly installed/upgraded binary's version is re-fetched.
    toolRow.versionedFor = null
    toolRow.version = null
    toolRow.belowMinVersionMessage = null
    val activeScope = scope
    if (activeScope == null) {
      toolRow.actionInProgress = false
      refreshRow(toolRow)
      return
    }
    var probeCallbacks = 0
    var versionResolvedFired = false
    toolRow.probeVersion(activeScope) { updatedRow ->
      probeCallbacks++
      if (!versionResolvedFired && updatedRow.version != null) {
        versionResolvedFired = true
        onVersionResolved(updatedRow.version)
      }
      // `probeVersion` fires onUpdated up to twice — once after path resolution, once after
      // the `--version` probe finishes — or just once if the path is unresolvable. The second
      // call (or a first call with no path) marks the end of the post-action refresh; that's
      // where the spinner finally gives way to ✓.
      val pathMissing = updatedRow.pathFieldValue is PathFieldValue.NotFound
      if (probeCallbacks >= 2 || pathMissing) {
        toolRow.actionInProgress = false
      }
      refreshRow(updatedRow)
    }
    activeScope.launch { refreshOutdated() }
  }

  /**
   * Reload the set of outdated tools, aggregated across every registered [PyToolManager] (so it works
   * without uv). Runs fully on background coroutines; the EDT is only touched via [onStateChanged].
   */
  private suspend fun refreshOutdated() {
    val eel = project.getEelDescriptor().toEelApi()
    val outdated = PyToolManagerProvider.managerFor(eel)?.list().orEmpty()
      .filterValues { PyPackageVersionComparator.STR_COMPARATOR.compare(it.latestVersion, it.installedVersion) > 0 }
      .map { (tool, info) -> tool.packageName.name to info.latestVersion }
      .toMap()
    if (outdated == outdatedVersions) return
    outdatedVersions = outdated
    withContext(Dispatchers.Main) { onStateChanged() }
  }
}
