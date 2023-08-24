// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class SimpleTerminalView(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProviderBase,
  session: TerminalSession,
  eventsHandler: TerminalEventsHandler,
  private val withVerticalScroll: Boolean = true
) : Disposable {
  private val editor: EditorImpl
  private val controller: SimpleTerminalController

  val component: JComponent = JPanel()
  val preferredFocusableComponent: JComponent
    get() = editor.contentComponent

  val terminalWidth: Int
    get() {
      val visibleArea = editor.scrollingModel.visibleArea
      val scrollBarWidth = editor.scrollPane.verticalScrollBar.width
      return visibleArea.width - scrollBarWidth
    }

  val charSize: Dimension
    get() = Dimension(editor.charHeight, editor.lineHeight)

  init {
    editor = createEditor()
    controller = SimpleTerminalController(settings, session, editor, eventsHandler)
    editor.addFocusListener(object : FocusChangeListener {
      override fun focusGained(editor: Editor) {
        controller.isFocused = true
      }

      override fun focusLost(editor: Editor) {
        controller.isFocused = false
      }
    })

    component.background = TerminalUi.terminalBackground
    component.border = JBUI.Borders.emptyLeft(TerminalUi.alternateBufferLeftInset)
    component.layout = BorderLayout()
    component.add(editor.component, BorderLayout.CENTER)
  }

  private fun createEditor(): EditorImpl {
    val document = DocumentImpl("", true)
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings)
    editor.settings.isLineMarkerAreaShown = false
    editor.scrollPane.verticalScrollBarPolicy = if (withVerticalScroll) {
      JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
    }
    else JScrollPane.VERTICAL_SCROLLBAR_NEVER
    return editor
  }

  fun isFocused(): Boolean = editor.contentComponent.hasFocus()

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
    Disposer.dispose(controller)
  }
}