package com.intellij.python.processOutput.frontend.ui.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.python.processOutput.frontend.ui.ProcessOutputUiContext
import com.intellij.ui.OnePixelSplitter
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class ProcessOutputToolWindow(project: Project, toolWindow: ToolWindow) {
  val component: JComponent
    field = SimpleToolWindowPanel(true, true)

  private val uiContext = ProcessOutputUiContext(project, component, toolWindow.disposable)

  init {
    val splitter = OnePixelSplitter(false, SPLITTER_PROPORTION_KEY, SPLITTER_DEFAULT_PROPORTION)

    splitter.firstComponent = ProcessTreeSection(uiContext).component
    splitter.secondComponent = JewelComposePanel { OutputSection(uiContext.controller) }

    val panel = JPanel(BorderLayout())

    panel.add(splitter)

    component.name = Naming.TOOL_WINDOW_PANEL_NAME
    component.setContent(panel)
  }

  private object Naming {
    const val TOOL_WINDOW_PANEL_NAME = "Python.ProcessOutput.ToolWindowPanel"
  }

  companion object {
    const val SPLITTER_PROPORTION_KEY = "Python.ProcessOutputToolWindow.Vertical"
    const val SPLITTER_DEFAULT_PROPORTION = 0.3f
  }
}
