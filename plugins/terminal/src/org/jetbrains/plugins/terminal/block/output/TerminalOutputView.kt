// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.find.SearchReplaceComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.block.TerminalFocusModel
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.ui.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.stickScrollBarToBottom
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLayeredPane
import kotlin.math.min

/**
 * Designed as a part of MVC pattern.
 * @see TerminalOutputModel
 * @see TerminalOutputView
 * @see TerminalOutputController
 */
internal class TerminalOutputView(
  private val project: Project,
  session: BlockTerminalSession,
  settings: JBTerminalSystemSettingsProviderBase,
  focusModel: TerminalFocusModel
) : Disposable {
  val controller: TerminalOutputController
  val component: JComponent
  val preferredFocusableComponent: JComponent
    get() = editor.contentComponent

  private val editor: EditorImpl

  init {
    editor = createEditor(settings)
    controller = TerminalOutputController(project, editor, session, settings, focusModel)
    component = TerminalOutputPanel()
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
    editor.useTerminalDefaultBackground(this)
    stickScrollBarToBottom(editor.scrollPane.verticalScrollBar)
    return editor
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
  }

  private inner class TerminalOutputPanel : JBLayeredPane(), UiDataProvider {
    init {
      isOpaque = false
      add(editor.component, JLayeredPane.DEFAULT_LAYER as Any)  // cast to Any needed to call right method overload
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.EDITOR] = editor
    }

    override fun getPreferredSize(): Dimension {
      return if (editor.document.textLength == 0) Dimension() else editor.preferredSize
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
