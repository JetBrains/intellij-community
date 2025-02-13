// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.ide.GeneralSettings
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalShellIntegrationEventsListener
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

internal class TerminalVfsSynchronizer private constructor() {

  companion object {
    fun install(sessionController: TerminalSessionController, view: ReworkedTerminalView, parentDisposable: Disposable) {
      sessionController.addShellIntegrationListener(parentDisposable, object : TerminalShellIntegrationEventsListener {
        override fun commandFinished(command: String, exitCode: Int) {
          SaveAndSyncHandler.getInstance().scheduleRefresh()
        }
      })
      view.addFocusListener(parentDisposable, object : FocusListener {
        override fun focusGained(e: FocusEvent) {
          if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
            WriteIntentReadAction.run {
              FileDocumentManager.getInstance().saveAllDocuments()
            }
          }
        }

        override fun focusLost(e: FocusEvent) {
          if (GeneralSettings.getInstance().isSyncOnFrameActivation) {
            // Like we sync the external changes when switching to the IDE window, let's do the same
            // when the focus is transferred from the built-in terminal to some other IDE place.
            // To get the updates from a long-running command in the built-in terminal.
            SaveAndSyncHandler.getInstance().scheduleRefresh()
          }
        }
      })
    }
  }
}
