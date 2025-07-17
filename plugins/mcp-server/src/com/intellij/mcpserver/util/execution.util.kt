package com.intellij.mcpserver.util

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.Action
import javax.swing.JComponent

suspend fun checkUserConfirmationIfNeeded(@NlsContexts.Label notificationText: String, command: String?, project: Project) {
  if (!McpServerSettings.getInstance().state.enableBraveMode && !askConfirmation(project,notificationText, command)) {
    throw McpExpectedError("User rejected command execution")
  }
}

suspend fun askConfirmation(project: Project, @NlsContexts.Label notificationText: String, command: String?): Boolean {
  return withContext(Dispatchers.EDT) {
    val confirmationDialog = object : DialogWrapper(project, true) {
      init {
        init()
        title = McpServerBundle.message("dialog.title.confirm.command.execution")
        okAction.putValue(Action.NAME, McpServerBundle.message("command.execution.confirmation.allow"))
        cancelAction.putValue(Action.NAME, McpServerBundle.message("command.execution.confirmation.deny"))
      }

      override fun createCenterPanel(): JComponent {
        return panel {
          row {
            label(notificationText)
          }

          if (command != null) {
            row {
              textArea()
                .text(command)
                .align(Align.FILL)
                .rows(10)
                .applyToComponent {
                  lineWrap = true
                  isEditable = false
                  font = EditorFontType.getGlobalPlainFont()
                }
            }
          }
          row {
            checkBox(McpServerBundle.message("checkbox.enable.brave.mode.skip.command.execution.confirmations"))
              .bindSelected(McpServerSettings.getInstance().state::enableBraveMode)
              .comment(McpServerBundle.message("text.note.you.can.enable.brave.mode.in.settings.to.skip.this.confirmation"))
          }
        }
      }
    }
    confirmationDialog.show()
    return@withContext confirmationDialog.isOK
  }
}