// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.python.execution.FailureReason
import com.jetbrains.python.execution.PyExecutionFailure
import com.jetbrains.python.execution.userMessage
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.text.StyleConstants

/**
 * @throws IllegalStateException if [project] is not `null` and it is disposed
 */
fun showProcessExecutionErrorDialog(
  project: Project?,
  exception: PyExecutionFailure,
) {
  check(project == null || !project.isDisposed)

  val errorMessageText = PyBundle.message("dialog.message.command.could.not.complete")
  // HTML format for text in `JBLabel` enables text wrapping
  val errorMessageLabel = JBLabel(UIUtil.toHtml(errorMessageText), Messages.getErrorIcon(), SwingConstants.LEFT)

  val commandOutputTextPane = JTextPane().apply {
    val command = (listOf(exception.command) + exception.args).joinToString(" ")
    when (val err = exception.failureReason) {
      FailureReason.CantStart -> {
        appendProcessOutput(command, "\n", exception.userMessage, null)

      }
      is FailureReason.ExecutionFailed -> {
        val output = err.output
        appendProcessOutput(command, output.stdout, output.stderr, output.exitCode)
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
    .addComponentFillVertically(commandOutputPanel, UIUtil.DEFAULT_VGAP)

  object : DialogWrapper(project) {
    init {
      init()
      title = exception.additionalMessage ?: errorMessageText
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = formBuilder.panel.apply {
      preferredSize = Dimension(820, 400)
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