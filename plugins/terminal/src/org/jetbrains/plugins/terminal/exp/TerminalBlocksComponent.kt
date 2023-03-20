// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.jediterm.core.util.TermSize
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.max

class TerminalBlocksComponent(private val project: Project,
                              private val session: TerminalSession,
                              private val settings: JBTerminalSystemSettingsProviderBase,
                              commandExecutor: TerminalCommandExecutor,
                              parentDisposable: Disposable) : JPanel() {
  private val blocksPanel: JPanel
  private val scrollPane: JBScrollPane
  private val promptPanel: TerminalPromptPanel = TerminalPromptPanel(project, settings, session, commandExecutor)

  private var runningPanel: TerminalPanel? = null

  init {
    Disposer.register(parentDisposable, promptPanel)

    blocksPanel = object : JPanel(VerticalLayout(0)) {
      override fun getBackground(): Color {
        return UIUtil.getTextFieldBackground()
      }
    }

    scrollPane = JBScrollPane(blocksPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
    stickScrollBarToBottom(scrollPane.verticalScrollBar)

    layout = BorderLayout()
    add(scrollPane, BorderLayout.CENTER)
    add(promptPanel, BorderLayout.SOUTH)
  }

  fun makeCurrentBlockReadOnly() {
    runningPanel?.makeReadOnly() ?: error("Running panel is null")
  }

  @RequiresEdt
  fun installRunningPanel() {
    val eventsHandler = TerminalEventsHandler(session.terminalStarter, session.model, settings)
    val panel = TerminalPanel(project, settings, session.model, eventsHandler)
    runningPanel = panel

    promptPanel.isVisible = false
    blocksPanel.add(panel, VerticalLayout.BOTTOM)
    blocksPanel.revalidate()

    IdeFocusManager.getInstance(project).requestFocus(panel.preferredFocusableComponent, true)
  }

  @RequiresEdt
  fun resetPromptPanel() {
    runningPanel = null

    promptPanel.reset()
    promptPanel.isVisible = true
    revalidate()
    repaint()

    IdeFocusManager.getInstance(project).requestFocus(promptPanel.preferredFocusableComponent, true)
  }

  @RequiresEdt
  fun toggleFullScreen(isFullScreen: Boolean) {
    val panel = runningPanel ?: error("Running panel is null")
    panel.toggleFullScreen(isFullScreen)
    if (isFullScreen) {
      remove(scrollPane)
      add(panel, BorderLayout.CENTER)
    }
    else {
      remove(panel)
      blocksPanel.add(panel, VerticalLayout.BOTTOM)
      add(scrollPane, BorderLayout.CENTER)
    }

    revalidate()
    repaint()
    IdeFocusManager.getInstance(project).requestFocus(panel.preferredFocusableComponent, true)
  }

  // return preferred size of the terminal calculated from the component size
  fun getTerminalSize(): TermSize {
    val promptWidth = promptPanel.getContentSize().width
    val componentSize = Dimension(promptWidth, this.height)
    val baseSize = calculateTerminalSize(componentSize, promptPanel.charSize)
    return ensureTermMinimumSize(baseSize)
  }

  private fun calculateTerminalSize(componentSize: Dimension, charSize: Dimension): TermSize {
    val width = componentSize.width / charSize.width
    val height = componentSize.height / charSize.height
    return TermSize(width, height)
  }

  private fun ensureTermMinimumSize(size: TermSize): TermSize {
    return TermSize(max(TerminalModel.MIN_WIDTH, size.columns), max(TerminalModel.MIN_HEIGHT, size.rows))
  }

  fun isFocused(): Boolean {
    return promptPanel.isFocused()
  }

  override fun getBackground(): Color {
    return UIUtil.getTextFieldBackground()
  }

  fun getPreferredFocusableComponent(): JComponent {
    return if (runningPanel != null) {
      runningPanel!!.preferredFocusableComponent
    }
    else promptPanel.preferredFocusableComponent
  }

  private fun stickScrollBarToBottom(verticalScrollBar: JScrollBar) {
    verticalScrollBar.model.addChangeListener(object : ChangeListener {
      var preventRecursion: Boolean = false
      var prevValue: Int = 0
      var prevMaximum: Int = 0
      var prevExtent: Int = 0

      override fun stateChanged(e: ChangeEvent?) {
        if (preventRecursion) return

        val model = verticalScrollBar.model
        val maximum = model.maximum
        val extent = model.extent

        if (extent != prevExtent || maximum != prevMaximum) {
          if (prevValue == prevMaximum - prevExtent) {
            preventRecursion = true
            model.value = maximum - extent
            preventRecursion = false
          }
        }

        prevValue = model.value
        prevMaximum = model.maximum
        prevExtent = model.extent
      }
    })
  }
}