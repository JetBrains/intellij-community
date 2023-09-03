// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class TerminalOutputView(
  private val project: Project,
  session: TerminalSession,
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
    controller = TerminalOutputController(editor, session, settings)
    component = TerminalOutputPanel()
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

  /**
   * This wrapper is needed to provide the editor to the DataContext.
   * Editor is not proving it itself, because renderer mode is enabled ([EditorImpl.isRendererMode]).
   */
  private inner class TerminalOutputPanel : JPanel(), DataProvider {
    init {
      isOpaque = false
      layout = BorderLayout()
      add(editor.component, BorderLayout.CENTER)
    }

    override fun getData(dataId: String): Any? {
      return if (CommonDataKeys.EDITOR.`is`(dataId)) {
        editor
      }
      else null
    }
  }
}