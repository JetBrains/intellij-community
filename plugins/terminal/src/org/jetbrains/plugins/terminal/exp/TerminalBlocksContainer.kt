// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.UIUtil
import com.jediterm.core.util.TermSize
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.max

class TerminalBlocksContainer(private val project: Project,
                              private val session: TerminalSession,
                              private val settings: JBTerminalSystemSettingsProviderBase) : JPanel(), ComponentContainer {
  private val blocksPanel: JPanel
  private val scrollPane: JBScrollPane
  private val promptPanel: TerminalPromptPanel
  private var runningPanel: TerminalPanel? = null

  init {
    val commandExecutor = object : TerminalCommandExecutor {
      override fun startCommandExecution(command: String) {
        onCommandStarted(command)
      }
    }
    promptPanel = TerminalPromptPanel(project, settings, commandExecutor)
    Disposer.register(this, promptPanel)

    session.addCommandListener(object : ShellCommandListener {
      override fun commandFinished(command: String, exitCode: Int, duration: Long) {
        invokeLater {
          onCommandFinished(command, exitCode, duration)
        }
      }
    }, parentDisposable = this)

    session.model.addTerminalListener(object : TerminalModel.TerminalListener {
      override fun onAlternateBufferChanged(enabled: Boolean) {
        invokeLater {
          toggleFullScreen(enabled)
        }
      }
    })

    blocksPanel = object : JPanel(VerticalLayout(0)) {
      override fun getBackground(): Color {
        return UIUtil.getTextFieldBackground()
      }
    }

    scrollPane = JBScrollPane(blocksPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
    stickScrollBarToBottom(scrollPane.verticalScrollBar)

    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        sizeTerminalToComponent()
      }
    })

    layout = BorderLayout()
    add(scrollPane, BorderLayout.CENTER)
    add(promptPanel, BorderLayout.SOUTH)
  }

  private fun onCommandStarted(command: String) {
    session.executeCommand(command)

    invokeLater {
      installRunningPanel()
    }
  }

  private fun installRunningPanel() {
    val eventsHandler = TerminalEventsHandler(session.terminalStarter, session.model, settings)
    val panel = TerminalPanel(project, settings, session.model, eventsHandler)
    runningPanel = panel

    promptPanel.isVisible = false
    blocksPanel.add(panel, VerticalLayout.BOTTOM)
    blocksPanel.revalidate()

    IdeFocusManager.getInstance(project).requestFocus(panel.preferredFocusableComponent, true)
  }

  private fun onCommandFinished(command: String, exitCode: Int, duration: Long) {
    runningPanel?.makeReadOnly() ?: error("Running panel is null")
    runningPanel = null

    val model = session.model
    model.lock()
    try {
      model.clearAllExceptPrompt()
    }
    finally {
      model.unlock()
    }

    promptPanel.reset()
    promptPanel.isVisible = true
    revalidate()
    repaint()

    IdeFocusManager.getInstance(project).requestFocus(promptPanel.preferredFocusableComponent, true)
  }

  private fun toggleFullScreen(isFullScreen: Boolean) {
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

  fun sizeTerminalToComponent() {
    val newSize = getTerminalSize()
    val model = session.model
    if (newSize.columns != model.width || newSize.rows != model.height) {
      // TODO: is it needed?
      //myTypeAheadManager.onResize()
      session.postResize(newSize)
    }
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

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent {
    return if (runningPanel != null) {
      runningPanel!!.preferredFocusableComponent
    }
    else promptPanel.preferredFocusableComponent
  }

  override fun dispose() {

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