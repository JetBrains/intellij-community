// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.util.preferredHeight
import com.intellij.util.ui.JBInsets
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.exp.TerminalPromptController.PromptStateListener
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

class BlockTerminalPanel(
  private val project: Project,
  private val session: TerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase
) : JPanel(), TerminalContentController, TerminalCommandExecutor {
  private val controller: BlockTerminalController

  private val outputPanel: TerminalOutputPanel
  private val promptPanel: TerminalPromptPanel
  private var alternateBufferPanel: TerminalPanel? = null

  init {
    outputPanel = TerminalOutputPanel(project, session, settings)
    promptPanel = TerminalPromptPanel(project, settings, session, this)

    promptPanel.controller.addListener(object : PromptStateListener {
      override fun promptVisibilityChanged(visible: Boolean) {
        promptPanel.component.isVisible = visible
        revalidate()
        invokeLater {
          IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
        }
      }
    })

    promptPanel.controller.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (promptPanel.component.preferredHeight != promptPanel.component.height) {
          revalidate()
        }
      }
    })

    outputPanel.controller.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (outputPanel.component.height < this@BlockTerminalPanel.height    // do not revalidate if output already occupied all height
            && outputPanel.component.preferredHeight > outputPanel.component.height) { // revalidate if output no more fit in current bounds
          revalidate()
        }
      }
    })

    controller = BlockTerminalController(session, outputPanel.controller, promptPanel.controller)

    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateTerminalSize()
      }
    })

    session.model.addTerminalListener(object : TerminalModel.TerminalListener {
      override fun onAlternateBufferChanged(enabled: Boolean) {
        invokeLater {
          alternateBufferStateChanged(enabled)
        }
      }
    })

    installPromptAndOutput()
  }

  private fun alternateBufferStateChanged(enabled: Boolean) {
    if (enabled) {
      installAlternateBufferPanel()
    }
    else {
      alternateBufferPanel?.let { Disposer.dispose(it) }
      alternateBufferPanel = null
      installPromptAndOutput()
    }
    IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
    invokeLater {
      updateTerminalSize()
    }
  }

  private fun installAlternateBufferPanel() {
    val eventsHandler = TerminalEventsHandler(session, settings)
    val panel = TerminalPanel(project, settings, session.model, eventsHandler, withVerticalScroll = false)
    Disposer.register(this, panel)
    alternateBufferPanel = panel

    removeAll()
    layout = BorderLayout()
    add(panel.component, BorderLayout.CENTER)
    revalidate()
  }

  private fun installPromptAndOutput() {
    removeAll()
    layout = BlockTerminalLayout()
    add(outputPanel.component, BlockTerminalLayout.TOP)
    add(promptPanel.component, BlockTerminalLayout.BOTTOM)
    revalidate()
  }

  override fun startCommandExecution(command: String) {
    controller.startCommandExecution(command)
  }

  override fun getBackground(): Color {
    return TerminalUI.terminalBackground
  }

  private fun updateTerminalSize() {
    val newSize = getTerminalSize() ?: return
    controller.resize(newSize)
  }

  override fun getTerminalSize(): TermSize? {
    val (width, charSize) = if (alternateBufferPanel != null) {
      alternateBufferPanel!!.let { it.terminalWidth to it.charSize }
    }
    else outputPanel.let { it.terminalWidth to it.charSize }
    return if (width > 0 && height > 0) {
      TerminalUiUtils.calculateTerminalSize(Dimension(width, height), charSize)
    }
    else null
  }

  override fun isFocused(): Boolean {
    return outputPanel.component.hasFocus() || promptPanel.component.hasFocus()
  }

  override fun dispose() {
    Disposer.dispose(outputPanel)
    Disposer.dispose(promptPanel)
  }

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent {
    return when {
      alternateBufferPanel != null -> alternateBufferPanel!!.preferredFocusableComponent
      promptPanel.component.isVisible -> promptPanel.preferredFocusableComponent
      else -> outputPanel.preferredFocusableComponent
    }
  }

  /**
   * This layout is needed to place [TOP] component (command blocks) over the [BOTTOM] component (prompt).
   * The height of the [TOP] component is limited by the container size minus preferred height of the [BOTTOM].
   * [com.intellij.ui.components.panels.VerticalLayout] and [com.intellij.ui.components.panels.ListLayout] can not be used instead,
   * since they set height to preferred height of the components.
   */
  private class BlockTerminalLayout : LayoutManager2 {
    companion object {
      const val TOP = "TOP"
      const val BOTTOM = "BOTTOM"
    }

    private var topComponent: Component? = null
    private var bottomComponent: Component? = null

    override fun addLayoutComponent(comp: Component?, constraints: Any?) {
      when (constraints) {
        TOP -> topComponent = comp
        BOTTOM -> bottomComponent = comp
        else -> throw IllegalArgumentException("Unknown constraint: $constraints")
      }
    }

    override fun addLayoutComponent(name: String?, comp: Component?) {
      addLayoutComponent(comp, name)
    }

    override fun layoutContainer(container: Container) {
      val size = container.size
      JBInsets.removeFrom(size, container.insets)
      val bottomHeight = bottomComponent?.let { doLayout(it, size.height, size.width) } ?: 0
      topComponent?.let { doLayout(it, size.height - bottomHeight, size.width) }
    }

    private fun doLayout(component: Component, bottomY: Int, maxWidth: Int): Int {
      val prefSize = if (component.isVisible) component.preferredSize else Dimension(0, 0)
      val minSize = if (component.isVisible) component.minimumSize else Dimension(0, 0)
      val width = max(maxWidth, minSize.width)
      val height = max(minSize.height, min(prefSize.height, bottomY))
      component.setBounds(0, bottomY - height, width, height)
      return height
    }

    override fun preferredLayoutSize(parent: Container?): Dimension = calculateSize { preferredSize }

    override fun minimumLayoutSize(parent: Container?): Dimension = calculateSize { minimumSize }

    override fun maximumLayoutSize(target: Container?): Dimension = calculateSize { maximumSize }

    private fun calculateSize(getSize: Component.() -> Dimension): Dimension {
      val sizes = listOfNotNull(topComponent, bottomComponent).map { getSize(it) }
      val width = sizes.maxOfOrNull { it.width } ?: 0
      val height = sizes.maxOfOrNull { it.height } ?: 0
      return Dimension(width, height)
    }

    override fun removeLayoutComponent(comp: Component?) {
      when (comp) {
        topComponent -> topComponent = null
        bottomComponent -> bottomComponent = null
      }
    }

    override fun getLayoutAlignmentX(target: Container?): Float = Component.CENTER_ALIGNMENT

    override fun getLayoutAlignmentY(target: Container?): Float = Component.BOTTOM_ALIGNMENT

    override fun invalidateLayout(target: Container?) {}
  }
}