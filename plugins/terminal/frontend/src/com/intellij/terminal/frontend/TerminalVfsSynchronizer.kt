package com.intellij.terminal.frontend

import com.intellij.ide.GeneralSettings
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

internal class TerminalVfsSynchronizer private constructor() {

  companion object {
    fun install(
      sessionController: TerminalSessionController,
      focusListenerRegistrar: (Disposable, FocusListener) -> Unit,
      parentDisposable: Disposable,
    ) {
      sessionController.addShellIntegrationListener(parentDisposable, object : TerminalShellIntegrationEventsListener {
        override fun commandFinished(command: String, exitCode: Int, currentDirectory: String) {
          SaveAndSyncHandler.getInstance().scheduleRefresh()
        }
      })
      focusListenerRegistrar(parentDisposable, object : FocusListener {
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