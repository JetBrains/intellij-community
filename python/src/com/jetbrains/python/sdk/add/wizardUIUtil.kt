// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.JBCardLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.messages.showProcessExecutionErrorDialog
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.packaging.PyExecutionException
import java.awt.*
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JPanel

internal fun doCreateSouthPanel(leftButtons: List<JButton>, rightButtons: List<JButton>): JPanel {
  val panel = JPanel(BorderLayout())
  //noinspection UseDPIAwareInsets
  val insets = if (SystemInfoRt.isMac)
    if (UIUtil.isUnderIntelliJLaF()) JBUI.insets(0, 8) else JBInsets.emptyInsets()
  else if (UIUtil.isUnderWin10LookAndFeel()) JBInsets.emptyInsets() else Insets(8, 0, 0, 0) //don't wrap to JBInsets

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
  val hgap = if (SystemInfoRt.isMac) if (UIUtil.isUnderIntelliJLaF()) 8 else 0 else 5
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

fun showProcessExecutionErrorDialog(project: Project?, e: PyExecutionException) =
  showProcessExecutionErrorDialog(project, e.localizedMessage.orEmpty(), e.command, e.stdout, e.stderr, e.exitCode)