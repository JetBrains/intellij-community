// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.processOutput.common.ProcessOutputQuery
import com.intellij.python.processOutput.common.QueryResponse
import com.intellij.python.processOutput.common.QueryResponsePayload.BooleanPayload
import com.intellij.python.processOutput.common.QueryResponsePayload.UnitPayload
import com.intellij.python.processOutput.common.sendProcessOutputQuery
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Font
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.text.StyleConstants

/**
 * @throws IllegalStateException if [project] is not `null` and it is disposed
 */
@ApiStatus.Internal
@RequiresEdt
fun showErrorDialog(
  project: Project?,
  execError: PyError,
) {
  when (execError) {
    is ExecError -> {
      showProcessExecutionErrorDialog(project, execError)
    }
    is MessageError -> {
      Messages.showErrorDialog(execError.message, CommonBundle.message("title.error"))
    }
  }
}

/**
 * @throws IllegalStateException if [project] is not `null` and it is disposed
 */
@ApiStatus.Internal
@RequiresEdt
fun showProcessExecutionErrorDialog(
  project: Project?,
  execError: ExecError,
) {
  check(project == null || !project.isDisposed)

  val processId = execError.loggedProcessId

  if (project != null && processId != null) {
    val hasOpenedModals = LaterInvocator.getCurrentModalEntities().isNotEmpty()
    attemptOpenErrorInProcessOutputToolWindow(project, execError, processId, hasOpenedModals)
  }
  else {
    showProcessExecutionErrorDialogModal(project, execError)
  }
}

@RequiresEdt
private fun showProcessExecutionErrorDialogModal(project: Project?, execError: ExecError) {
  val errorMessageText = PyBundle.message("dialog.message.command.could.not.complete")
  // HTML format for text in `JBLabel` enables text wrapping
  val errorMessageLabel = JBLabel(UIUtil.toHtml(errorMessageText), Messages.getErrorIcon(), SwingConstants.LEFT)

  val commandOutputTextPane = JTextPane().apply {
    val command = execError.asCommand
    when (val err = execError.errorReason) {
      is ExecErrorReason.CantStart -> {
        appendProcessOutput(command, err.cantExecProcessError, execError.message, null)

      }
      is ExecErrorReason.UnexpectedProcessTermination -> {
        appendProcessOutput(command, err.stdoutString, err.stderrString, err.exitCode)
      }
      ExecErrorReason.Timeout -> {
        appendProcessOutput(command, "Timeout", "\n", null)
      }
    }

    background = JBColor.WHITE
    isEditable = false
  }

  val commandOutputPanel = BorderLayoutPanel().apply {
    border = IdeBorderFactory.createTitledBorder(IdeBundle.message("border.title.command.output"), false)

    addToCenter(
      JBScrollPane(commandOutputTextPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER))
  }

  val formBuilder = FormBuilder()
    .addComponent(errorMessageLabel)
    .addComponent(JButton(PyBundle.message("python.error.full.log")).apply {
      addActionListener {
        Messages.showErrorDialog(execError.message, PyBundle.message("python.error"))
      }
    })
    .addComponentFillVertically(commandOutputPanel, UIUtil.DEFAULT_VGAP)

  object : DialogWrapper(project) {
    init {
      init()
      title = execError.additionalMessageToUser ?: errorMessageText
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = formBuilder.panel.apply {
      preferredSize = Dimension(820, 400)
    }
  }.showAndGet()
}

private fun attemptOpenErrorInProcessOutputToolWindow(
  project: Project,
  execError: ExecError,
  processId: Int,
  hasOpenedModals: Boolean,
) {
  val service = ApplicationManager.getApplication().service<ErrorDialogService>()

  service.coroutineScope.launch {
    val additionalMessage = execError.additionalMessageToUser
    val toolWindowWasOpened = run {
      if (additionalMessage != null) {
        val additionalDataResponse =
          sendProcessOutputQuery(
            ProcessOutputQuery.SpecifyAdditionalMessageToUser(
              processId,
              additionalMessage,
            )
          )

        when (additionalDataResponse) {
          is QueryResponse.Timeout<UnitPayload> -> return@run false
          is QueryResponse.Completed<UnitPayload> -> {}
        }
      }

      if (hasOpenedModals) {
        return@run false
      }

      val openToolWindowResponse =
        sendProcessOutputQuery(
          ProcessOutputQuery.OpenToolWindowWithError(
            processId
          )
        )

      return@run when (openToolWindowResponse) {
        is QueryResponse.Timeout<BooleanPayload> -> false
        is QueryResponse.Completed<BooleanPayload> -> openToolWindowResponse.payload.value
      }
    }

    if (!toolWindowWasOpened) {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        writeIntentReadAction {
          showProcessExecutionErrorDialogModal(project, execError)
        }
      }
    }
  }
}

private fun JTextPane.appendProcessOutput(command: String, stdout: String, stderr: String, exitCode: Int?) {
  val stdoutStyle = addStyle(null, null)
  StyleConstants.setFontFamily(stdoutStyle, Font.MONOSPACED)

  val stderrStyle = addStyle(null, stdoutStyle)
  StyleConstants.setForeground(stderrStyle, JBColor.RED)

  document.apply {
    insertString(0, command + "\n", stdoutStyle)
    arrayOf(stdout to stdoutStyle, stderr to stderrStyle).forEach { (std, style) ->
      if (std.isNotEmpty()) insertString(length, std + "\n", style)
    }
    if (exitCode != null) {
      insertString(length, "Process finished with exit code $exitCode", stdoutStyle)
    }
  }
}

@Service
private class ErrorDialogService(val coroutineScope: CoroutineScope)