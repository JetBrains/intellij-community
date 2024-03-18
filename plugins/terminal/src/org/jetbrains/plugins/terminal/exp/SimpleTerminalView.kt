// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.intellij.util.ui.JBUI
import com.jediterm.terminal.ui.AwtTransformers
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class SimpleTerminalView(
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

  val charSize: Dimension
    get() = Dimension(editor.charHeight, editor.lineHeight)

  init {
    val palette = session.colorPalette
    editor = createEditor(palette)
    controller = SimpleTerminalController(settings, session, editor)
    component = SimpleTerminalPanel(palette)
    editor.addFocusListener(object : FocusChangeListener {
      override fun focusGained(editor: Editor) {
        controller.isFocused = true
      }

      override fun focusLost(editor: Editor) {
        controller.isFocused = false
      }
    })
  }

  private fun createEditor(palette: TerminalColorPalette): EditorImpl {
    val document = DocumentImpl("", true)
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings)
    editor.backgroundColor = AwtTransformers.toAwtColor(palette.defaultBackground)!!
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
  private inner class SimpleTerminalPanel(palette: TerminalColorPalette) : JPanel(), DataProvider {
    init {
      background = AwtTransformers.toAwtColor(palette.defaultBackground)
      border = JBUI.Borders.emptyLeft(TerminalUi.alternateBufferLeftInset)
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