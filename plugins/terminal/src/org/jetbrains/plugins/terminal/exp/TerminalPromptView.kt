// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.exp.TerminalPromptController.PromptStateListener
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class TerminalPromptView(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProviderBase,
  session: TerminalSession,
  commandExecutor: TerminalCommandExecutor
) : PromptStateListener, Disposable {
  val controller: TerminalPromptController
  val component: JComponent = JPanel()

  val preferredFocusableComponent: JComponent
    get() = editor.contentComponent

  private val editor: EditorImpl
  private val promptLabel: JLabel
  private val commandHistoryPresenter: CommandHistoryPresenter

  init {
    val editorTextField = createPromptTextField(session)
    editor = editorTextField.getEditor(true) as EditorImpl
    controller = TerminalPromptController(editor, session, commandExecutor)
    controller.addListener(this)

    promptLabel = createPromptLabel()
    promptLabel.text = controller.computePromptText(TerminalProjectOptionsProvider.getInstance(project).startingDirectory ?: "")

    commandHistoryPresenter = CommandHistoryPresenter(project, editor, commandExecutor)

    val innerBorder = JBUI.Borders.empty(TerminalUi.promptTopInset,
                                         TerminalUi.blockLeftInset + TerminalUi.cornerToBlockInset,
                                         TerminalUi.promptBottomInset,
                                         TerminalUi.blockRightInset + TerminalUi.cornerToBlockInset)
    val outerBorder = JBUI.Borders.customLineTop(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
    component.border = JBUI.Borders.compound(outerBorder, innerBorder)

    component.background = TerminalUi.terminalBackground
    component.layout = ListLayout.vertical(TerminalUi.promptToCommandInset)
    component.add(promptLabel)
    component.add(editorTextField)
  }

  override fun promptLabelChanged(newText: @NlsSafe String) {
    runInEdt { promptLabel.text = newText }
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

  private fun createPromptTextField(session: TerminalSession): LanguageTextField {
    val shellType = session.shellIntegration?.shellType
    val language = if (shellType != null) {
      TerminalShellSupport.findByShellType(shellType)?.promptLanguage ?: PlainTextLanguage.INSTANCE
    }
    else PlainTextLanguage.INSTANCE
    val textField = object : LanguageTextField(language, project, "", false) {
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
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.gutterComponentEx.isPaintBackground = false
    editor.backgroundColor = TerminalUi.terminalBackground
    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = settings.lineSpacing
    }
    editor.settings.isBlockCursor = true

    return textField
  }

  private fun createPromptLabel(): JLabel {
    val label = object : JLabel() {
      override fun updateUI() {
        super.updateUI()
        font = EditorUtil.getEditorFont()
      }
    }
    label.foreground = TerminalUi.promptForeground
    label.alignmentX = JComponent.LEFT_ALIGNMENT
    return label
  }

  override fun dispose() {}
}