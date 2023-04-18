// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.ui.JBUI
import com.jediterm.core.util.TermSize
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import kotlin.math.max

class PlainTerminalController(
  project: Project,
  private val session: TerminalSession,
  settings: JBTerminalSystemSettingsProviderBase
) : TerminalContentController {
  private val panel: TerminalPanel

  init {
    val eventsHandler = TerminalEventsHandler(session, settings)
    panel = TerminalPanel(project, settings, session.model, eventsHandler)
    panel.border = JBUI.Borders.empty()
    panel.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        val newSize = getTerminalSize() ?: return
        session.postResize(newSize)
      }
    })

    Disposer.register(this, panel)
  }

  // return preferred size of the terminal calculated from the component size
  override fun getTerminalSize(): TermSize {
    val contentSize = panel.getContentSize()
    val size = calculateTerminalSize(contentSize, panel.charSize)
    return ensureTermMinimumSize(size)
  }

  private fun calculateTerminalSize(componentSize: Dimension, charSize: Dimension): TermSize {
    val width = componentSize.width / charSize.width
    val height = componentSize.height / charSize.height
    return TermSize(width, height)
  }

  private fun ensureTermMinimumSize(size: TermSize): TermSize {
    return TermSize(max(TerminalModel.MIN_WIDTH, size.columns), max(TerminalModel.MIN_HEIGHT, size.rows))
  }

  override fun isFocused(): Boolean {
    return panel.isFocused()
  }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusableComponent(): JComponent = panel.preferredFocusableComponent

  override fun dispose() {
  }
}