// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.processOutput.common.ProcessOutputTopic
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.PyBundle
import com.jetbrains.python.TraceContext
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.management.PyPackageScope
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Public entry point that opens the "Change version" version-chooser popup for a single package.
 *
 * [showChangeVersionPopup] and its collaborators (`PyPackageScope`, `PythonPackageDetails`) are
 * `internal` to this module because they lean on the packaging service's private types; this
 * launcher is `object`-public so callers in downstream modules — e.g. the redesigned "Workspace
 * Structure" settings page (PY-89840) — can open the popup without going through the packaging
 * tool window.
 *
 * Details for [packageName] are fetched via the package manager's repository manager (no repo
 * filter, so any enabled repo can provide the metadata). If the fetch fails the process-output
 * tool window is surfaced instead of a silent no-op, matching how the tool-window path handles
 * the same error.
 */
@ApiStatus.Internal
object PyChangeVersionPopupLauncher {
  /**
   * @param workspaceMember The uv/poetry workspace member the target package belongs to, so the
   * downstream `uv add --package <member> <pkg>` (or equivalent) writes back to the member's
   * `pyproject.toml` instead of the workspace root. Pass `null` for pip environments and for
   * packages that live directly at the workspace root.
   */
  fun open(
    project: Project,
    sdk: Sdk,
    packageName: String,
    currentVersion: String? = null,
    anchor: RelativePoint? = null,
    workspaceMember: PyWorkspaceMember? = null,
  ) {
    val manager = PythonPackageManager.forSdk(project, sdk)
    PyPackageCoroutine.launch(project, Dispatchers.Default) {
      val trace = TraceContext(PyBundle.message("trace.context.packaging.tool.window.change.version", packageName), null)
      val details = manager.repositoryManager.getPackageDetails(packageName, null).getOrNull()
      if (details == null) {
        ProcessOutputTopic.sendOpenToolWindowByTraceUuidEvent(trace.uuid)
        return@launch
      }
      val scope = if (workspaceMember == null) PyPackageScope.NONE else PyPackageScope(workspaceMember = workspaceMember)
      withContext(Dispatchers.EDT) {
        showChangeVersionPopup(
          project = project,
          details = details,
          scope = scope,
          anchor = anchor,
          highlightVersion = null,
          currentVersion = currentVersion,
        )
      }
    }
  }
}
