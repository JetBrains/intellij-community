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

class BlockTerminalView(
  private val project: Project,
  private val session: TerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase
) : TerminalContentView, TerminalCommandExecutor {
  private val controller: BlockTerminalController

  private val outputView: TerminalOutputView = TerminalOutputView(project, session, settings)
  private val promptView: TerminalPromptView = TerminalPromptView(project, settings, session, this)
  private var alternateBufferView: SimpleTerminalView? = null

  override val component: JComponent = JPanel()

  override val preferredFocusableComponent: JComponent
    get() = when {
      alternateBufferView != null -> alternateBufferView!!.preferredFocusableComponent
      promptView.component.isVisible -> promptView.preferredFocusableComponent
      else -> outputView.preferredFocusableComponent
    }

  init {
    Disposer.register(this, outputView)
    Disposer.register(this, promptView)

    promptView.controller.addListener(object : PromptStateListener {
      override fun promptVisibilityChanged(visible: Boolean) {
        promptView.component.isVisible = visible
        component.revalidate()
        invokeLater {
          IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
        }
      }
    })

    promptView.controller.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (promptView.component.preferredHeight != promptView.component.height) {
          component.revalidate()
        }
      }
    })

    outputView.controller.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (outputView.component.height < component.height    // do not revalidate if output already occupied all height
            && outputView.component.preferredHeight > outputView.component.height) { // revalidate if output no more fit in current bounds
          component.revalidate()
        }
      }
    })

    val focusModel = TerminalFocusModel(project, outputView, promptView)
    controller = BlockTerminalController(session, focusModel, outputView.controller, promptView.controller)

    component.addComponentListener(object : ComponentAdapter() {
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

    component.background = TerminalUi.terminalBackground
    installPromptAndOutput()
  }

  private fun alternateBufferStateChanged(enabled: Boolean) {
    if (enabled) {
      installAlternateBufferPanel()
    }
    else {
      alternateBufferView?.let { Disposer.dispose(it) }
      alternateBufferView = null
      installPromptAndOutput()
    }
    IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
    invokeLater {
      updateTerminalSize()
    }
  }

  private fun installAlternateBufferPanel() {
    val eventsHandler = TerminalEventsHandler(session, settings)
    val view = SimpleTerminalView(project, settings, session, eventsHandler, withVerticalScroll = false)
    Disposer.register(this, view)
    alternateBufferView = view

    with(component) {
      removeAll()
      layout = BorderLayout()
      add(view.component, BorderLayout.CENTER)
      revalidate()
    }
  }

  private fun installPromptAndOutput() {
    with(component) {
      removeAll()
      layout = BlockTerminalLayout()
      add(outputView.component, BlockTerminalLayout.TOP)
      add(promptView.component, BlockTerminalLayout.BOTTOM)
      revalidate()
    }
  }

  override fun startCommandExecution(command: String) {
    controller.startCommandExecution(command)
  }

  private fun updateTerminalSize() {
    val newSize = getTerminalSize() ?: return
    controller.resize(newSize)
  }

  override fun getTerminalSize(): TermSize? {
    val (width, charSize) = if (alternateBufferView != null) {
      alternateBufferView!!.let { it.terminalWidth to it.charSize }
    }
    else outputView.let { it.terminalWidth to it.charSize }
    return if (width > 0 && component.height > 0) {
      TerminalUiUtils.calculateTerminalSize(Dimension(width, component.height), charSize)
    }
    else null
  }

  override fun isFocused(): Boolean {
    return outputView.component.hasFocus() || promptView.component.hasFocus()
  }

  override fun dispose() {}

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