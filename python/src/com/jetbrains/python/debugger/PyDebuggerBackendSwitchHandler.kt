@file:Suppress("SpellCheckingInspection")
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyDebuggerBackendSwitchHandler {
  fun isApplicable(project: Project): Boolean

  /**
   * Shows a confirmation dialog if needed.
   * Returns false if the user canceled; in that case sessions are not affected.
   */
  fun confirmSwitch(project: Project): Boolean = true

  /**
   * Called after all handlers confirmed and the new backend has been set.
   * Should stop or restart own sessions to apply the change.
   */
  fun handleSessions(project: Project)

  /**
   * Returns true if this handler is responsible for the debug session in the current Debug tool window tab.
   * When true, [shouldShowSwitcher] is called to determine whether to show the switcher for that tab.
   */
  fun ownsRunProfile(e: AnActionEvent): Boolean = false

  /**
   * Returns true if the switcher should be visible for the current Debug tool window tab.
   * Called only when [ownsRunProfile] returned true for the same event.
   */
  fun shouldShowSwitcher(project: Project, e: AnActionEvent): Boolean = true

  /**
   * Returns true if debugpy is available for the current SDK.
   */
  fun isDebugpyAvailableForSdk(project: Project, sdk: Sdk): Boolean

  /**
   * Called after the backend has been switched via the toolbar or post-install prompt.
   */
  fun onBackendSwitched(project: Project, from: PyDebuggerBackend, to: PyDebuggerBackend) {}

  /**
   * Called when the user clicks "Report an Issue with debugpy".
   */
  fun onReportIssueClicked(project: Project?) {}

  /**
   * Returns the "how to collect debugpy logs" instruction for the issue-report template when this
   * handler owns the currently active debug session, or null if it does not apply.
   */
  fun getReportIssueLogInstruction(project: Project): String? = null

  companion object {
    val EP_NAME: ExtensionPointName<PyDebuggerBackendSwitchHandler> =
      ExtensionPointName.create("com.jetbrains.python.debugger.backendSwitchHandler")
  }
}
