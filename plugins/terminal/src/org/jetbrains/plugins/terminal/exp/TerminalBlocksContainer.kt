// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.TermSize
import com.intellij.util.ui.ImageUtil
import com.jediterm.terminal.RequestOrigin
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.max

class TerminalBlocksContainer(private val project: Project,
                              private val settings: JBTerminalSystemSettingsProviderBase) : JPanel(), ComponentContainer {
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
    promptPanel = TerminalPromptPanel(project, commandExecutor)
    Disposer.register(this, promptPanel)

    layout = BorderLayout()
    add(promptPanel, BorderLayout.CENTER)
  }

  private fun startTerminalSession(command: String) {
    val visibleRect = promptPanel.preferredFocusableComponent.visibleRect
    val componentSize = Dimension(visibleRect.width, visibleRect.height)
    val size = calculateTerminalSize(componentSize)
    val session = TerminalSession(project, settings, size)
    session.start()
    session.executeCommand(command)
    invokeLater {
      installRunningPanel(session)
    }
  }

  private fun installRunningPanel(session: TerminalSession) {
    val eventsHandler = TerminalEventsHandler(session.terminalStarter, session.model, settings)
    val panel = TerminalPanel(project, settings, session.model, eventsHandler)
    runningPanel = panel

    panel.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        val visibleRect = panel.preferredFocusableComponent.visibleRect
        val componentSize = Dimension(visibleRect.width, visibleRect.height)
        val newSize = calculateTerminalSize(componentSize)
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

    removeAll()
    add(panel, BorderLayout.CENTER)
    revalidate()
    repaint()

    IdeFocusManager.getInstance(project).requestFocus(panel.preferredFocusableComponent, true)
  }

  private fun calculateTerminalSize(componentSize: Dimension): Dimension {
    val charSize = calculateCharSize()
    val width = componentSize.width / charSize.width
    val height = componentSize.height / charSize.height
    return Dimension(width, height)
  }

  private fun calculateCharSize(): Dimension {
    val img: BufferedImage = ImageUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val graphics = img.createGraphics().also { it.font = settings.terminalFont }
    try {
      val metrics = graphics.fontMetrics
      val width = metrics.charWidth('W')
      val metricsHeight = metrics.height
      val height = ceil(metricsHeight * settings.lineSpacing).toInt()
      return Dimension(width, height)
    }
    finally {
      img.flush()
      graphics.dispose()
    }
  }

  private fun ensureTermMinimumSize(size: Dimension) {
    size.setSize(max(TerminalModel.MIN_WIDTH, size.width), max(TerminalModel.MIN_HEIGHT, size.height))
  }

  fun isFocused(): Boolean {
    return promptPanel.isFocused()
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
}