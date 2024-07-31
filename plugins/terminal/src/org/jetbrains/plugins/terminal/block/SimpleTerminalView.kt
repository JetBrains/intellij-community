// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.block.ui.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.getCharSize
import java.awt.BorderLayout
import java.awt.geom.Dimension2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * When you call a process which requires "alternative buffer" (less, vim, htop, mc, etc.)
 * We drop our terminal editor and create a new editor for this alternative buffer.
 */
internal class SimpleTerminalView(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProviderBase,
  session: BlockTerminalSession,
  private val withVerticalScroll: Boolean = true
) : Disposable {
  private val editor: EditorImpl
  val controller: SimpleTerminalController

  val component: JComponent
  val preferredFocusableComponent: JComponent
    get() = editor.contentComponent

  val terminalWidth: Int
    get() {
      val visibleArea = editor.scrollingModel.visibleArea
      val scrollBarWidth = editor.scrollPane.verticalScrollBar.width
      return visibleArea.width - scrollBarWidth
    }

  val charSize: Dimension2D
    get() = editor.getCharSize()

  init {
    editor = createEditor()
    controller = SimpleTerminalController(settings, session, editor)
    component = SimpleTerminalPanel(editor)
    editor.addFocusListener(object : FocusChangeListener {
      override fun focusGained(editor: Editor) {
        controller.isFocused = true
      }

      override fun focusLost(editor: Editor) {
        controller.isFocused = false
      }
    })
  }

  private fun createEditor(): EditorImpl {
    val document = DocumentImpl("", true)
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings)
    editor.useTerminalDefaultBackground(this)
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

  /**
   * This wrapper is needed to provide the editor to the DataContext.
   * Editor is not proving it itself, because renderer mode is enabled ([EditorImpl.isRendererMode]).
   */
  private inner class SimpleTerminalPanel(editor: Editor) : JPanel(), UiDataProvider {
    init {
      background = TerminalUi.defaultBackground(editor)
      border = JBUI.Borders.emptyLeft(TerminalUi.alternateBufferLeftInset)
      layout = BorderLayout()
      add(editor.component, BorderLayout.CENTER)
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.EDITOR] = editor
    }
  }
}
