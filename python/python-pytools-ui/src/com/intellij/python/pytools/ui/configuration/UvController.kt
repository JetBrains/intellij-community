// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pytools.statistics.PyToolUsagesCollector
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
   * Names of tools currently installed by `uv tool install` (i.e. listed by `uv tool list`).
   * Populated asynchronously after [uvAvailable] is confirmed; drives whether the Path column's
   * hover icon is the plain version-info icon or the upgrade-via-uv button.
   */
  @Volatile
  var uvManagedNames: Set<String> = emptySet()
    private set

  /** True iff the tool's primary package is currently installed by uv (per the cached list). */
  fun isUvManaged(toolRow: ToolRow): Boolean =
    uvAvailable.get() == true && toolRow.tool.packageName.name in uvManagedNames

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
  fun installViaUv(toolRow: ToolRow) = runUvToolAction(
    toolRow = toolRow,
    progressTitleKey = "settings.external.tools.install.via.uv.progress",
    errorTitleKey = "settings.external.tools.install.via.uv.error.title",
    action = { uvTool, name -> uvTool.install(name) },
    onSuccess = { PyToolUsagesCollector.Helper.logToolInstalled(project, toolRow.tool) },
  )

  /** Run `uv tool upgrade <packageName>` with modal progress; refresh the row when it succeeds. */
  fun upgradeViaUv(toolRow: ToolRow) = runUvToolAction(
    toolRow = toolRow,
    progressTitleKey = "settings.external.tools.upgrade.via.uv.progress",
    errorTitleKey = "settings.external.tools.upgrade.via.uv.error.title",
    action = { uvTool, name -> uvTool.upgrade(name) },
    onSuccess = { PyToolUsagesCollector.Helper.logToolUpdated(project, toolRow.tool) },
  )

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
  ) {
    val packageName = toolRow.tool.installInfo.packageName.name
    val title = PyToolsUiBundle.message(progressTitleKey, toolRow.tool.presentableName)
    val errorTitle = PyToolsUiBundle.message(errorTitleKey, toolRow.tool.presentableName)
    val result = runWithModalProgressBlocking(project, title) {
      val uvPath = getUvExecutableLocal() ?: return@runWithModalProgressBlocking null
      action(createUvToolRuntime(uvPath).uvCli().tool(), packageName)
    }
    if (result == null) {
      Messages.showErrorDialog(project, PyToolsUiBundle.message("settings.external.tools.install.via.uv.error.no.uv"), errorTitle)
      return
    }
    val failure = result as? Result.Failure<*>
    if (failure != null) {
      Messages.showErrorDialog(project, failure.error.toString(), errorTitle)
      return
    }
    onSuccess()
    // Invalidate the cached probe so the freshly installed/upgraded binary's version is re-fetched.
    toolRow.versionedFor = null
    toolRow.version = null
    toolRow.belowMinVersionMessage = null
    val activeScope = scope ?: return
    toolRow.probeVersion(activeScope, onUpdated = refreshRow)
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
    val installed = createUvToolRuntime(uvPath).uvCli().tool().listInstalled().getOr { return }
    val names = installed.map { it.name }.toSet()
    if (names == uvManagedNames) return
    uvManagedNames = names
    withContext(Dispatchers.Main) { onStateChanged() }
  }
}
