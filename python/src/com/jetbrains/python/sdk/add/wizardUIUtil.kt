// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBCardLayout
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.python.packaging.PyExecutionException
import java.awt.*
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.text.StyleConstants

internal fun doCreateSouthPanel(leftButtons: List<JButton>, rightButtons: List<JButton>): JPanel {
  val panel = JPanel(BorderLayout())
  //noinspection UseDPIAwareInsets
  val insets = if (SystemInfo.isMacOSLeopard)
    if (UIUtil.isUnderIntelliJLaF()) JBUI.insets(0, 8) else JBUI.emptyInsets()
  else if (UIUtil.isUnderWin10LookAndFeel()) JBUI.emptyInsets() else Insets(8, 0, 0, 0) //don't wrap to JBInsets

  val bag = GridBag().setDefaultInsets(insets)

  val lrButtonsPanel = NonOpaquePanel(GridBagLayout())

  val leftButtonsPanel = createButtonsPanel(leftButtons)
  leftButtonsPanel.border = BorderFactory.createEmptyBorder(0, 0, 0, 20)  // leave some space between button groups
  lrButtonsPanel.add(leftButtonsPanel, bag.next())

  lrButtonsPanel.add(Box.createHorizontalGlue(), bag.next().weightx(1.0).fillCellHorizontally())   // left strut

  val buttonsPanel = createButtonsPanel(rightButtons)

  lrButtonsPanel.add(buttonsPanel, bag.next())

  panel.add(lrButtonsPanel, BorderLayout.CENTER)

  panel.border = JBUI.Borders.emptyTop(8)

  return panel
}

private fun createButtonsPanel(buttons: List<JButton>): JPanel {
  val hgap = if (SystemInfo.isMacOSLeopard) if (UIUtil.isUnderIntelliJLaF()) 8 else 0 else 5
  val buttonsPanel = NonOpaquePanel(GridLayout(1, buttons.size, hgap, 0))
  buttons.forEach { buttonsPanel.add(it) }
  return buttonsPanel
}

internal fun swipe(panel: JPanel, stepContent: Component, swipeDirection: JBCardLayout.SwipeDirection) {
  val stepContentName = stepContent.hashCode().toString()

  panel.add(stepContentName, stepContent)
  (panel.layout as JBCardLayout).swipe(panel, stepContentName, swipeDirection)
}

internal fun show(panel: JPanel, stepContent: Component) {
  val stepContentName = stepContent.hashCode().toString()

  panel.add(stepContentName, stepContent)
  (panel.layout as CardLayout).show(panel, stepContentName)
}

internal fun showProcessExecutionErrorDialog(project: Project?, e: PyExecutionException) {
  val errorMessageText = "${e.command} could not complete successfully. " +
                         "Please see the command's output for information about resolving this problem."
  // HTML format for text in `JBLabel` enables text wrapping
  val errorMessageLabel = JBLabel(UIUtil.toHtml(errorMessageText), Messages.getErrorIcon(), SwingConstants.LEFT)

  val commandOutputTextPane = JTextPane().apply {
    appendProcessOutput(e.stdout, e.stderr, e.exitCode)

    background = JBColor.WHITE
    isEditable = false
  }

  val commandOutputPanel = BorderLayoutPanel().apply {
    border = IdeBorderFactory.createTitledBorder("Command output", false)

    addToCenter(JBScrollPane(commandOutputTextPane, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER))
  }

  val formBuilder = FormBuilder()
    .addComponent(errorMessageLabel)
    .addComponentFillVertically(commandOutputPanel, UIUtil.DEFAULT_VGAP)

  object : DialogWrapper(project) {
    init {
      init()
      title = e.localizedMessage
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = formBuilder.panel.apply {
      preferredSize = Dimension(600, 300)
    }
  }.showAndGet()
}

private fun JTextPane.appendProcessOutput(stdout: String, stderr: String, exitCode: Int) {
  val stdoutStyle = addStyle(null, null)
  StyleConstants.setFontFamily(stdoutStyle, Font.MONOSPACED)
  val stderrStyle = addStyle(null, stdoutStyle)
  StyleConstants.setForeground(stderrStyle, JBColor.RED)
  document.apply {
    arrayOf(stdout to stdoutStyle, stderr to stderrStyle).forEach { (std, style) ->
      if (std.isNotEmpty()) insertString(length, std + "\n", style)
    }
    insertString(length, "Process finished with exit code $exitCode", stdoutStyle)
  }
}