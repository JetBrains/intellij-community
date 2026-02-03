// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.AppLifecycleListener
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage

internal class TerminalNewUserTracker : AppLifecycleListener {
  init {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode ||
        application.isHeadlessEnvironment ||
        // We need to track the new user state only in monolith or JetBrains Client.
        AppMode.isRemoteDevHost()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun appStarted() {
    val storage = TerminalUsageLocalStorage.getInstance()
    if (InitialConfigImportState.isNewUser()) {
      val moment = TerminalFirstIdeSessionMoment.Version(BuildNumber.currentVersion())
      storage.recordFirstIdeSessionMoment(moment)
    }
    else if (storage.state.firstIdeSessionMoment == null) {
      storage.recordFirstIdeSessionMoment(TerminalFirstIdeSessionMoment.BeforeTrackingStarted)
    }
  }

  companion object {
    /**
     * Returns true if a user started using the IDE in the current release.
     */
    fun isNewUserForRelease(): Boolean {
      val moment = TerminalUsageLocalStorage.getInstance().state.firstIdeSessionMoment ?: return false
      return moment is TerminalFirstIdeSessionMoment.Version
             && moment.build.baselineVersion == BuildNumber.currentVersion().baselineVersion
    }
  }
}