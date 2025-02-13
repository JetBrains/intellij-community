// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalShellIntegrationEventsListener

internal class TerminalVfsSynchronizer private constructor() {

  companion object {
    fun install(sessionController: TerminalSessionController, parentDisposable: Disposable) {
      sessionController.addShellIntegrationListener(parentDisposable, object : TerminalShellIntegrationEventsListener {
        override fun commandFinished(command: String, exitCode: Int) {
          SaveAndSyncHandler.getInstance().scheduleRefresh()
        }
      })
    }
  }
}
