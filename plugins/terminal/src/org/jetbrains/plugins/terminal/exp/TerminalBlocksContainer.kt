// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
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
                              private val settings: JBTerminalSystemSettingsProviderBase) : JPanel(), ComponentContainer {
  private val blocksPanel: JPanel
  private val scrollPane: JBScrollPane
  private val promptPanel: TerminalPromptPanel
  private var runningPanel: TerminalPanel? = null

  init {
    val commandExecutor = object : TerminalCommandExecutor {
      override fun startCommandExecution(command: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
          startTerminalSession(command)
        }
      }
    }
    promptPanel = TerminalPromptPanel(project, settings, commandExecutor)
    Disposer.register(this, promptPanel)

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

  private fun startTerminalSession(command: String) {
    val componentSize = promptPanel.getContentSize()
    val charSize = TerminalUiUtils.calculateCharSize(settings.terminalFont, settings.lineSpacing)
    val size = calculateTerminalSize(componentSize, charSize)

    val session = TerminalSession(project, settings, size)
    session.start()
    session.executeCommand(command)

    invokeLater {
      installRunningPanel(session)

      // Close session in one second just to demonstrate finished block
      val closeSession: () -> Unit = {
        commandExecutionFinished(session)
      }
      Alarm(Alarm.ThreadToUse.SWING_THREAD).addRequest(closeSession, 1000)
    }
  }

  private fun installRunningPanel(session: TerminalSession) {
    val eventsHandler = TerminalEventsHandler(session.terminalStarter, session.model, settings)
    val panel = TerminalPanel(project, settings, session.model, eventsHandler)
    runningPanel = panel

    panel.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        val componentSize = panel.getContentSize()
        val newSize = calculateTerminalSize(componentSize, panel.charSize)
        ensureTermMinimumSize(newSize)

        val model = session.model
        if (newSize.width != model.width || newSize.height != model.height) {
          // TODO: is it needed?
          //myTypeAheadManager.onResize()
          val termSize = TermSize(newSize.width, newSize.height)
          session.terminalStarter.postResize(termSize, RequestOrigin.User)
        }
      }
    })

    promptPanel.isVisible = false
    blocksPanel.add(panel, VerticalLayout.BOTTOM)
    blocksPanel.revalidate()

    IdeFocusManager.getInstance(project).requestFocus(panel.preferredFocusableComponent, true)
  }

  private fun commandExecutionFinished(session: TerminalSession) {
    Disposer.dispose(session)

    runningPanel?.makeReadOnly() ?: error("Running panel is null")
    runningPanel = null

    promptPanel.reset()
    promptPanel.isVisible = true
    revalidate()
    repaint()

    IdeFocusManager.getInstance(project).requestFocus(promptPanel.preferredFocusableComponent, true)
  }

  private fun calculateTerminalSize(componentSize: Dimension, charSize: Dimension): Dimension {
    val width = componentSize.width / charSize.width
    val height = componentSize.height / charSize.height
    return Dimension(width, height)
  }

  private fun ensureTermMinimumSize(size: Dimension) {
    size.setSize(max(TerminalModel.MIN_WIDTH, size.width), max(TerminalModel.MIN_HEIGHT, size.height))
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