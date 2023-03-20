// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.LanguageTextField
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import java.awt.Color
import java.awt.Dimension
import javax.swing.*

class TerminalPromptPanel(private val project: Project,
                          private val settings: JBTerminalSystemSettingsProviderBase,
                          session: TerminalSession,
                          private val commandExecutor: TerminalCommandExecutor) : JPanel(), ComponentContainer, ShellCommandListener {
  private val editorTextField: LanguageTextField
  private val editor: EditorImpl
    get() = editorTextField.getEditor(true) as EditorImpl
  private val document: Document
    get() = editorTextField.document

  private val promptLabel: JLabel

  val charSize: Dimension
    get() = Dimension(editor.charHeight, editor.lineHeight)

  init {
    editorTextField = createPromptTextField()

    promptLabel = createPromptLabel()
    promptLabel.text = computePromptText(TerminalProjectOptionsProvider.getInstance(project).startingDirectory ?: "")

    editor.putUserData(SESSION_KEY, session)

    session.addCommandListener(this, parentDisposable = this)

    val innerBorder = JBUI.Borders.customLine(UIUtil.getTextFieldBackground(), 6, 0, 6, 0)
    val outerBorder = JBUI.Borders.customLineTop(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
    border = JBUI.Borders.compound(outerBorder, innerBorder)

    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(promptLabel)
    add(Box.createRigidArea(JBDimension(0, 4)))
    add(editorTextField)
  }

  private fun createPromptTextField(): LanguageTextField {
    val textField = object : LanguageTextField(FileTypes.PLAIN_TEXT.language, project, "", false) {
      override fun setBackground(bg: Color?) {
        // do nothing to not set background to editor in super method
      }
    }
    textField.setDisposedWith(this)
    textField.border = JBUI.Borders.emptyLeft(JBUI.scale(LEFT_INSET))
    textField.alignmentX = JComponent.LEFT_ALIGNMENT

    val editor = textField.getEditor(true)!!
    editor.backgroundColor = UIUtil.getTextFieldBackground()
    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = settings.lineSpacing
    }
    editor.settings.isBlockCursor = true
    editor.putUserData(KEY, this)  // to access this panel from editor action handlers

    return textField
  }

  private fun createPromptLabel(): JLabel {
    val label = JLabel()
    label.font = EditorUtil.getEditorFont()
    label.border = JBUI.Borders.emptyLeft(JBUI.scale(LEFT_INSET))
    label.alignmentX = JComponent.LEFT_ALIGNMENT
    return label
  }

  override fun directoryChanged(newDirectory: @NlsSafe String) {
    invokeLater {
      promptLabel.text = computePromptText(newDirectory)
    }
  }

  private fun computePromptText(directory: String): @NlsSafe String {
    return if (directory != SystemProperties.getUserHome()) {
      FileUtil.getLocationRelativeToUserHome(directory)
    }
    else "~"
  }

  @RequiresEdt
  fun reset() {
    runWriteAction {
      document.setText("")
    }
  }

  fun handleEnterPressed() {
    commandExecutor.startCommandExecution(document.text)
  }

  fun isFocused(): Boolean {
    return editor.contentComponent.hasFocus()
  }

  fun getContentSize(): Dimension {
    return Dimension(editor.component.width, editor.contentComponent.height)
  }

  override fun getBackground(): Color {
    return UIUtil.getTextFieldBackground()
  }

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent = editor.contentComponent

  override fun dispose() {

  }

  companion object {
    val KEY: Key<TerminalPromptPanel> = Key.create("TerminalPromptPanel")
    val SESSION_KEY: Key<TerminalSession> = Key.create("TerminalSession")

    private const val LEFT_INSET: Int = 7
  }
}