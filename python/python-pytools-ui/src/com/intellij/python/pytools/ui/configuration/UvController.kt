// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Version as PlatformVersion
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pytools.statistics.PyToolUsagesCollector
import com.intellij.python.pytools.statistics.PyToolActionSource
import com.intellij.python.pytools.Version
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.python.uv.backend.cli.uv.UvTool
import com.intellij.python.uv.backend.runtime.createUvToolRuntime
import com.intellij.python.uv.backend.runtime.uvCli
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.installExecutableViaPythonScript
import com.jetbrains.python.sdk.uv.impl.getUvExecutableLocal
import com.jetbrains.python.sdk.uv.impl.hasUvExecutableLocal
import com.jetbrains.python.sdk.uv.impl.setUvExecutableLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Owns everything uv-related on the External Tools page: the in-memory uv state snapshot, the
 * one-shot uv-availability detection, the uv-managed-tool list refresh, and the install / upgrade
 * actions invoked from the Path column's hover icon (and the install-uv-itself action invoked
 * from the footer hint).
 *
 * The configurable hands the controller a [CoroutineScope] via [onShown] — typically the scope
 * provided by `JComponent.launchOnShow`, so detection and background tasks live for the panel's
 * showing-lifetime and are cancelled automatically when the page is hidden. Two callbacks bridge
 * back to the configurable on EDT:
 *  - [onStateChanged] whenever uv state changes (availability / version / managed-tool set) so
 *    the configurable can refresh the table and rebuild the footer hint;
 *  - [refreshRow] forwarded into [ToolRow.probeVersion] after a successful install/upgrade so
 *    the freshly resolved version flows back into the row.
 */
internal class UvController(
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

  /** uv version reported by `uv self version --short`; null means not yet known. */
  @Volatile
  var uvVersion: String? = null
    private set

  /**
   * True iff the cached [uvVersion] is at least [UV_OUTDATED_SUPPORTED_SINCE], i.e. `uv tool list --outdated`
   * is available. While [uvVersion] is `null` (detection in flight or failed), this stays `false` so we fall
   * back to the legacy "try to upgrade" behaviour rather than miscalling a flag that isn't there.
   */
  val supportsOutdated: Boolean
    get() = uvVersion?.let { PlatformVersion.parseVersion(it) }?.let { it >= UV_OUTDATED_SUPPORTED_SINCE } == true

  /**
   * Names of tools currently installed by `uv tool install` (i.e. listed by `uv tool list`).
   * Populated asynchronously after [uvAvailable] is confirmed; drives whether the Path column's
   * hover icon is the plain version-info icon or the upgrade-via-uv button.
   */
  @Volatile
  var uvManagedNames: Set<String> = emptySet()
    private set

  /**
   * Names of uv-managed tools that have a newer release available, per `uv tool list --outdated`.
   * Maps the package name to the latest version uv would upgrade it to, so callers can surface
   * "Update to X.Y.Z" without re-querying. Only populated when [supportsOutdated] is true; with
   * older uv versions the legacy fallback uses [attemptedUpgrades] to decide whether the update
   * icon should still be shown.
   */
  @Volatile
  var uvOutdatedVersions: Map<String, String> = emptyMap()
    private set

  /**
   * Names of uv-managed tools whose upgrade was already attempted from this settings page in the
   * current session. Used only when [supportsOutdated] is false: once we've kicked off `uv tool
   * upgrade <name>` we can't know whether anything changed (uv's exit code is the same either way),
   * so we hide the icon so the user isn't tempted to keep clicking the same "try" button.
   */
  @Volatile
  private var attemptedUpgrades: Set<String> = emptySet()

  /**
   * Package name uv knows this row's tool by — a single value, never a set. Derived from the
   * basename of the detected binary, matched against the tool's `aliases`: for Pyright
   * (aliases `[pyright, basedpyright]`) this resolves to whichever of the two was actually
   * found on disk, and every uv operation (lookup, install/upgrade, attempted-upgrade tracking)
   * targets that same package. When nothing is detected the row is showing INSTALL and we fall
   * back to `installInfo.packageName.name` — the package the INSTALL click would put on disk.
   */
  private fun ToolRow.uvPackageName(): String {
    val detectedPath = when (val pfv = pathFieldValue) {
      is PathFieldValue.Custom -> pfv.path
      is PathFieldValue.AutoDetected -> pfv.path
      else -> null
    }
    if (detectedPath != null) {
      val baseName = detectedPath.fileName.toString().removeSuffix(".exe")
      tool.aliases.firstOrNull { it.name == baseName }?.let { return it.name }
    }
    return tool.installInfo.packageName.name
  }

  /** True iff the detected package is currently installed by uv (per the cached list). */
  fun isUvManaged(toolRow: ToolRow): Boolean =
    uvAvailable.get() == true && toolRow.uvPackageName() in uvManagedNames

  /**
   * Whether the upgrade icon should be offered for [toolRow]. Two regimes:
   *  - modern uv (`supportsOutdated`) — only when the detected package is known-outdated;
   *  - legacy uv — always, unless the detected package has already been upgraded, after
   *    which the icon is suppressed (we have no way to confirm whether anything changed).
   */
  fun isUpgradeAvailable(toolRow: ToolRow): Boolean {
    val name = toolRow.uvPackageName()
    return if (supportsOutdated) name in uvOutdatedVersions
    else name !in attemptedUpgrades
  }

  /**
   * Latest version uv would upgrade [toolRow]'s tool to, when known. Only returns non-null on
   * modern uv where `--outdated` reports versions; the legacy path has no way to know in advance.
   */
  fun latestVersionFor(toolRow: ToolRow): String? =
    uvOutdatedVersions[toolRow.uvPackageName()]

  /**
   * Called by the configurable from within `launchOnShow`'s coroutine. Stores [scope] for
   * click-driven actions and kicks off the initial uv-availability detection.
   */
  fun onShown(scope: CoroutineScope) {
    this.scope = scope
    scope.launch {
      val available = hasUvExecutableLocal()
      uvAvailable.set(available)
      uvVersion = if (available) fetchUvVersion() else null
      withContext(Dispatchers.Main) { onStateChanged() }
      if (available) refreshUvManagedNames()
    }
  }

  /** Run `uv tool install <packageName>` with modal progress; refresh the row when it succeeds. */
  fun installViaUv(toolRow: ToolRow, source: PyToolActionSource) = runUvToolAction(
    toolRow = toolRow,
    progressTitleKey = "settings.external.tools.install.via.uv.progress",
    errorTitleKey = "settings.external.tools.install.via.uv.error.title",
    action = { uvTool, name -> uvTool.install(name) },
    onSuccess = {
      PyToolUsagesCollector.Helper.logToolInstalled(project, toolRow.tool, source)
      // Baseline copy — surfaced while the post-action `--version` probe is still running and
      // we don't yet know the freshly installed version.
      toolRow.lastSuccessMessage =
        PyToolsUiBundle.message("settings.external.tools.install.via.uv.success.balloon", toolRow.uvPackageName())
    },
    onVersionResolved = { installedVersion ->
      // [runUvToolAction] only fires this hook when the probed version is non-null, so we can
      // safely format it into the version-bearing message; otherwise the baseline copy from
      // [onSuccess] stays.
      if (installedVersion != null) {
        toolRow.lastSuccessMessage = PyToolsUiBundle.message(
          "settings.external.tools.install.via.uv.success.to.version.balloon",
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
  fun upgradeViaUv(toolRow: ToolRow, source: PyToolActionSource) {
    // Snapshot the pre-upgrade version so we can tell, after the post-action re-probe, whether
    // the install actually changed anything (e.g. it could have already been latest).
    val previousVersion = toolRow.version
    runUvToolAction(
      toolRow = toolRow,
      progressTitleKey = "settings.external.tools.upgrade.via.uv.progress",
      errorTitleKey = "settings.external.tools.upgrade.via.uv.error.title",
      action = { uvTool, name -> uvTool.install(name, reinstall = true) },
      onSuccess = {
        PyToolUsagesCollector.Helper.logToolUpdated(project, toolRow.tool, source)
        // Legacy fallback: remember the detected package was upgraded so the icon stops
        // re-offering it. Tracked under the same name [isUpgradeAvailable] later reads.
        attemptedUpgrades = attemptedUpgrades + toolRow.uvPackageName()
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
        PyToolsUiBundle.message("settings.external.tools.upgrade.via.uv.up.to.date.balloon", name, current)
      current != null ->
        PyToolsUiBundle.message("settings.external.tools.upgrade.via.uv.success.to.version.balloon", name, current)
      else ->
        PyToolsUiBundle.message("settings.external.tools.upgrade.via.uv.success.balloon", name)
    }
  }

  /**
   * Install `uv` itself by piggybacking on the same Python script used by the New Project / Setup
   * SDK flow (`installExecutableViaPythonScript`). On success, persists the path with
   * `setUvExecutableLocal` (matching the SDK setup flow), flips [uvAvailable] on, and fires
   * [onStateChanged] so the configurable can rebuild the footer hint and the table.
   */
  fun installUv() {
    val title = PyToolsUiBundle.message("settings.external.tools.install.uv.progress")
    val errorTitle = PyToolsUiBundle.message("settings.external.tools.install.uv.error.title")
    val result = runWithModalProgressBlocking(project, title) {
      val systemPython = SystemPythonService()
                           .findSystemPythons().firstOrNull()
                         ?: return@runWithModalProgressBlocking null
      installExecutableViaPythonScript(systemPython.asExecutablePython.binary, "-n", "uv")
    }
    if (result == null) {
      Messages.showErrorDialog(project, PyToolsUiBundle.message("settings.external.tools.install.uv.error.no.python"), errorTitle)
      return
    }
    val failure = result as? Result.Failure<*>
    if (failure != null) {
      Messages.showErrorDialog(project, failure.error.toString(), errorTitle)
      return
    }
    val installedPath = (result as? Result.Success<*>)?.result as? Path ?: return
    setUvExecutableLocal(installedPath)
    uvAvailable.set(true)
    onStateChanged()
    // Fetch the freshly installed uv's version + the list of uv-managed tools off the EDT.
    scope?.launch {
      uvVersion = fetchUvVersion()
      withContext(Dispatchers.Main) { onStateChanged() }
      refreshUvManagedNames()
    }
  }

  /**
   * Shared driver for `uv tool install` / `uv tool upgrade`. Runs [action] under modal progress,
   * surfaces errors via a message dialog, and on success invalidates the row's cached probe and
   * refreshes the uv-managed list (so an Install can flip the row into "uv-managed" state).
   */
  private fun runUvToolAction(
    toolRow: ToolRow,
    progressTitleKey: String,
    errorTitleKey: String,
    action: suspend (UvTool, String) -> PyResult<String>,
    onSuccess: () -> Unit = {},
    /**
     * Fires once after the post-action `--version` re-probe publishes a version (or skips if
     * the probe never produces one before the row is reset). The argument is the freshly probed
     * version, suitable for comparing against a caller-captured snapshot.
     */
    onVersionResolved: (Version?) -> Unit = {},
  ) {
    // Whatever the alias detection settled on (or the install-info fallback when nothing is
    // detected) is what we pass to uv — keeps every step of this flow targeted at the same
    // package: install/reinstall, the post-action `--version` re-probe, and the
    // [attemptedUpgrades] / [uvOutdatedVersions] lookups in [isUpgradeAvailable].
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
      runWithModalProgressBlocking(project, title) {
        val uvPath = getUvExecutableLocal() ?: return@runWithModalProgressBlocking null
        action(createUvToolRuntime(uvPath).uvCli().tool(), packageName)
      }
    }
    catch (e: CancellationException) {
      toolRow.actionInProgress = false
      refreshRow(toolRow)
      throw e
    }
    if (result == null) {
      toolRow.actionInProgress = false
      refreshRow(toolRow)
      Messages.showErrorDialog(project, PyToolsUiBundle.message("settings.external.tools.install.via.uv.error.no.uv"), errorTitle)
      return
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
    activeScope.launch { refreshUvManagedNames() }
  }

  /** Best-effort `uv self version --short` lookup; null on any error. */
  private suspend fun fetchUvVersion(): String? {
    val uvPath = getUvExecutableLocal() ?: return null
    val result = createUvToolRuntime(uvPath).uvCli().self().version(short = true)
    val raw = (result as? Result.Success<*>)?.result as? String ?: return null
    return raw.trim().takeIf { it.isNotEmpty() }
  }

  /**
   * Reload the cached set of tools installed by `uv tool install` (used by the Path-cell hover
   * icon to decide between the plain version-info icon and the upgrade-via-uv button). Runs
   * fully on background coroutines; the EDT is only touched via [onStateChanged].
   */
  private suspend fun refreshUvManagedNames() {
    val uvPath = getUvExecutableLocal() ?: return
    val tool = createUvToolRuntime(uvPath).uvCli().tool()
    val installed = tool.listInstalled().getOr { return }
    val names = installed.map { it.name }.toSet()
    val outdated = if (supportsOutdated) {
      tool.listOutdated().getOr { return }.associate { it.name to it.latestVersion }
    }
    else emptyMap()
    if (names == uvManagedNames && outdated == uvOutdatedVersions) return
    uvManagedNames = names
    uvOutdatedVersions = outdated
    withContext(Dispatchers.Main) { onStateChanged() }
  }

  companion object {
    /** First uv release that exposes `--outdated` on `uv tool list` (released 2026-03-13). */
    private val UV_OUTDATED_SUPPORTED_SINCE: PlatformVersion = PlatformVersion(0, 10, 10)
  }
}
