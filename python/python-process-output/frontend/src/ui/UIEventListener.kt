package com.intellij.python.processOutput.frontend.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.ModalContextProjectLocator
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.python.processOutput.common.ExecErrorDto
import com.intellij.python.processOutput.common.ExecErrorReasonDto
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.ProcessOutputBundle
import com.intellij.python.processOutput.frontend.ProcessOutputController
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.Font
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.text.StyleConstants

internal class UIEventListener(
  private val uiContext: ProcessOutputUiContext,
  private val toolWindow: ToolWindow,
) {
  fun launch() =
    uiContext.coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      uiContext.controller.events.collect { event ->
        when (event) {
          is ProcessOutputController.Event.DisplayExecError -> {
            displayExecError(event.execErrorDto, event.associatedProcess)
          }
          is ProcessOutputController.Event.DisplayToolWindow -> {
            displayToolWindow(event.processToSelect)
          }
          is ProcessOutputController.Event.StatusUpdate -> {
            uiContext.processTree?.repaint()
          }
        }
      }
    }

  private suspend fun displayExecErrorInModal(error: ExecErrorDto) {
    writeIntentReadAction {
      showProcessExecutionErrorDialogModal(uiContext.project, error)
    }
  }

  private suspend fun displayExecError(
    error: ExecErrorDto,
    associatedProcess: LoggedProcess?,
  ) {
    val hasOpenedModalsOnFE =
      LaterInvocator.getCurrentModalEntities().any { it !is ModalContextProjectLocator }

    if (hasOpenedModalsOnFE || associatedProcess == null) {
      displayExecErrorInModal(error)
      return
    }

    displayToolWindow(associatedProcess)
  }

  private fun displayToolWindow(processToSelect: LoggedProcess?) {
    if (processToSelect != null) {
      uiContext.scrollOnProcessDisplayed = ProcessOutputUiContext.ScrollOnProcessDisplayed.Down(processToSelect.data.id)
      uiContext.controller.selectProcess(processToSelect)
      uiContext.controller.search("")
      if (!uiContext.controller.outputSectionState.isOutputExpanded.value) {
        uiContext.controller.toggleProcessOutput()
      }
    }

    toolWindow.show()
  }

  private fun showProcessExecutionErrorDialogModal(project: Project?, execError: ExecErrorDto) {
    val errorMessageText = ProcessOutputBundle.message("dialog.message.error.command.could.not.complete")
    // HTML format for text in `JBLabel` enables text wrapping
    val errorMessageLabel = JBLabel(UIUtil.toHtml(errorMessageText), Messages.getErrorIcon(), SwingConstants.LEFT)

    val commandOutputTextPane = JTextPane().apply {
      val command = execError.command
      when (val err = execError.reason) {
        is ExecErrorReasonDto.CantStart -> {
          appendProcessOutput(command, err.cantExecProcessError, execError.message, null)
        }
        is ExecErrorReasonDto.UnexpectedTermination -> {
          appendProcessOutput(command, err.stdout, err.stderr, err.exitCode)
        }
        ExecErrorReasonDto.Timeout -> {
          appendProcessOutput(command, "Timeout", "\n", null)
        }
      }

      background = JBColor.WHITE
      isEditable = false
    }

    val commandOutputPanel = BorderLayoutPanel().apply {
      border = IdeBorderFactory.createTitledBorder(IdeBundle.message("border.title.command.output"), false)

      addToCenter(
        JBScrollPane(
          commandOutputTextPane,
          ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
        )
      )
    }

    val fullLogButton = JButton(ProcessOutputBundle.message("dialog.message.error.full.log"))

    fullLogButton.addActionListener {
      Messages.showErrorDialog(execError.message, ProcessOutputBundle.message("dialog.message.error"))
    }

    val formBuilder =
      FormBuilder()
        .addComponent(errorMessageLabel)
        .addComponent(fullLogButton)
        .addComponentFillVertically(commandOutputPanel, UIUtil.DEFAULT_VGAP)

    object : DialogWrapper(project) {
      init {
        init()
        title = execError.additionalMessageToUser ?: errorMessageText
      }

      override fun createActions(): Array<Action> =
        arrayOf(okAction)

      override fun createCenterPanel(): JComponent {
        formBuilder.panel.preferredSize = Dimension(820, 400)
        return formBuilder.panel
      }
    }.showAndGet()
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
}
