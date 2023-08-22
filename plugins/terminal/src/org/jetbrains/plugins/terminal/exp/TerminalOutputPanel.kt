// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import java.awt.Dimension
import javax.swing.JComponent

class TerminalOutputPanel(
  private val project: Project,
  session: TerminalSession,
  settings: JBTerminalSystemSettingsProviderBase
) : ComponentContainer {
  val controller: TerminalOutputController

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
    controller = TerminalOutputController(editor, session, settings)

    editor.addFocusListener(object : FocusChangeListener {
      override fun focusGained(editor: Editor) {
        controller.isFocused = true
      }

      override fun focusLost(editor: Editor) {
        controller.isFocused = false
      }
    })
  }

  private fun createEditor(settings: JBTerminalSystemSettingsProviderBase): EditorImpl {
    val document = DocumentImpl("", true)
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings)
    editor.settings.isUseSoftWraps = true
    return editor
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
  }

  override fun getComponent(): JComponent = editor.component

  override fun getPreferredFocusableComponent(): JComponent = editor.contentComponent
}