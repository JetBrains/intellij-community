// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.find.SearchReplaceComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.util.preferredHeight
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.action.TerminalInterruptCommandAction
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JScrollBar
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.min

class TerminalOutputView(
  private val project: Project,
  session: BlockTerminalSession,
  settings: JBTerminalSystemSettingsProviderBase
) : Disposable {
  val controller: TerminalOutputController
  val component: JComponent
  val preferredFocusableComponent: JComponent
    get() = editor.contentComponent

  private val editor: EditorImpl

  val terminalWidth: Int
    get() {
      val visibleArea = editor.scrollingModel.visibleArea
      val scrollBarWidth = editor.scrollPane.verticalScrollBar.width
      return visibleArea.width - scrollBarWidth
    }

  val charSize: Dimension
    get() = Dimension(editor.charHeight, editor.lineHeight)

  init {
    editor = createEditor(settings)
    controller = TerminalOutputController(project, editor, session, settings)
    component = TerminalOutputPanel()

    controller.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        invokeLater {
          if (editor.isDisposed) return@invokeLater
          val editorComponent = editor.component
          if (editorComponent.height < component.height    // do not revalidate if output already occupied all height
              && editorComponent.preferredHeight > editorComponent.height) { // revalidate if output no more fit in current bounds
            component.revalidate()
            component.repaint()
          }
        }
      }
    })
  }

  @RequiresEdt
  fun installSearchComponent(searchComponent: SearchReplaceComponent) {
    component.add(searchComponent, JLayeredPane.POPUP_LAYER as Any)  // cast to Any needed to call right method overload
    component.revalidate()
    component.repaint()
  }

  @RequiresEdt
  fun removeSearchComponent(searchComponent: SearchReplaceComponent) {
    component.remove(searchComponent)
    component.revalidate()
    component.repaint()
  }

  private fun createEditor(settings: JBTerminalSystemSettingsProviderBase): EditorImpl {
    val document = DocumentImpl("", true)
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings)
    editor.settings.isUseSoftWraps = true
    stickScrollBarToBottom(editor.scrollPane.verticalScrollBar)
    TerminalInterruptCommandAction().registerCustomShortcutSet(editor.contentComponent, null)
    return editor
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
          // stay at the bottom if the previous position was at the bottom
          if (prevValue == prevMaximum - prevExtent) {
            preventRecursion = true
            try {
              model.value = maximum - extent
            }
            finally {
              preventRecursion = false
            }
          }
        }

        prevValue = model.value
        prevMaximum = model.maximum
        prevExtent = model.extent
      }
    })
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
  }

  private inner class TerminalOutputPanel : JBLayeredPane(), DataProvider {
    init {
      isOpaque = false
      add(editor.component, JLayeredPane.DEFAULT_LAYER as Any)  // cast to Any needed to call right method overload
    }

    override fun getData(dataId: String): Any? {
      return if (CommonDataKeys.EDITOR.`is`(dataId)) {
        editor
      }
      else null
    }

    override fun doLayout() {
      for (component in components) {
        when (component) {
          editor.component -> layoutEditor(component)
          is SearchReplaceComponent -> layoutSearchComponent(component)
        }
      }
    }

    private fun layoutEditor(component: Component) {
      val prefHeight = component.preferredSize.height
      val compHeight = min(height, prefHeight)
      component.setBounds(0, height - compHeight, width, compHeight)
    }

    private fun layoutSearchComponent(component: Component) {
      val prefSize = component.preferredSize
      val maxSize = component.maximumSize
      val compWidth = minOf(width, prefSize.width, maxSize.width)
      val compHeight = min(prefSize.height, maxSize.height)
      component.setBounds(width - compWidth, 0, compWidth, compHeight)
    }
  }
}