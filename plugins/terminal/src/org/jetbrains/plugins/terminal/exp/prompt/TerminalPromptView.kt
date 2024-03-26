// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.LanguageTextField
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.TerminalCommandExecutor
import org.jetbrains.plugins.terminal.exp.TerminalInlineCompletion
import org.jetbrains.plugins.terminal.exp.TerminalUi
import org.jetbrains.plugins.terminal.exp.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.exp.history.CommandHistoryPresenter
import org.jetbrains.plugins.terminal.exp.history.CommandSearchPresenter
import org.jetbrains.plugins.terminal.exp.prompt.TerminalPromptController.PromptStateListener
import org.jetbrains.plugins.terminal.exp.prompt.lang.TerminalPromptLanguage
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class TerminalPromptView(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProviderBase,
  session: BlockTerminalSession,
  commandExecutor: TerminalCommandExecutor
) : PromptStateListener, Disposable {
  val controller: TerminalPromptController
  val component: JComponent = JPanel()

  val preferredFocusableComponent: JComponent
    get() = editor.contentComponent

  private val editor: EditorImpl
  private val commandHistoryPresenter: CommandHistoryPresenter
  private val commandSearchPresenter: CommandSearchPresenter

  init {
    val editorTextField = createPromptTextField(session)
    editor = editorTextField.getEditor(true) as EditorImpl
    controller = TerminalPromptController(editor, session, commandExecutor)
    controller.addListener(this)

    commandHistoryPresenter = CommandHistoryPresenter(project, editor, controller.model, commandExecutor)
    commandSearchPresenter = CommandSearchPresenter(project, editor, controller.model)

    val innerBorder = JBUI.Borders.empty(TerminalUi.promptTopInset,
                                         TerminalUi.blockLeftInset + TerminalUi.cornerToBlockInset,
                                         TerminalUi.promptBottomInset,
                                         0)
    val outerBorder = object : CustomLineBorder(TerminalUi.promptSeparatorColor(editor),
                                                JBInsets(1, 0, 0, 0)) {
      override fun paintBorder(c: Component, g: Graphics?, x: Int, y: Int, w: Int, h: Int) {
        // Paint the border only if the component is not on the top
        if (c.y != 0) {
          super.paintBorder(c, g, x, y, w, h)
        }
      }
    }
    component.border = JBUI.Borders.compound(outerBorder, innerBorder)

    component.background = TerminalUi.defaultBackground(editor)
    component.layout = ListLayout.vertical(TerminalUi.promptToCommandInset)
    component.add(editorTextField)

    // move focus to the prompt text field on mouse click in the area of the prompt
    component.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true)
      }
    })
  }

  override fun commandHistoryStateChanged(showing: Boolean) {
    if (showing) {
      val history = controller.commandHistory
      if (history.isNotEmpty()) {
        commandHistoryPresenter.showCommandHistory(history)
      }
    }
    else {
      commandHistoryPresenter.onCommandHistoryClosed()
    }
  }

  override fun commandSearchRequested() {
    val history = controller.commandHistory
    if (history.isNotEmpty()) {
      commandSearchPresenter.showCommandSearch(history)
    }
  }

  private fun createPromptTextField(session: BlockTerminalSession): LanguageTextField {
    val textField = object : LanguageTextField(TerminalPromptLanguage, project, "", false) {
      override fun setBackground(bg: Color?) {
        // do nothing to not set background to editor in super method
      }

      override fun updateUI() {
        super.updateUI()
        font = EditorUtil.getEditorFont()
      }
    }
    textField.setDisposedWith(this)
    textField.alignmentX = JComponent.LEFT_ALIGNMENT

    val editor = textField.getEditor(true) as EditorImpl
    editor.settings.isUseSoftWraps = true
    editor.settings.isShowingSpecialChars = false
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    editor.setVerticalScrollbarVisible(true)
    editor.gutterComponentEx.isPaintBackground = false
    editor.useTerminalDefaultBackground(this)
    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = 1.0f
      // to not paint the custom background under the prompt
      setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null)
    }
    editor.caretModel.primaryCaret.visualAttributes = CaretVisualAttributes(null, CaretVisualAttributes.Weight.HEAVY)
    editor.putUserData(AutoPopupController.SHOW_BOTTOM_PANEL_IN_LOOKUP_UI, false)

    FileDocumentManager.getInstance().getFile(editor.document)?.let {
      editor.setFile(it)
    }
    TerminalInlineCompletion.getInstance(project).install(editor)

    editor.contextMenuGroupId = "Terminal.PromptContextMenu"

    return textField
  }

  override fun dispose() {}
}