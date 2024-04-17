// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.LanguageTextField
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.exp.TerminalPromptController.PromptStateListener
import org.jetbrains.plugins.terminal.exp.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.exp.history.CommandHistoryPresenter
import org.jetbrains.plugins.terminal.exp.history.CommandSearchPresenter
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

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
  private val promptComponent: SimpleColoredComponent
  private val commandHistoryPresenter: CommandHistoryPresenter
  private val commandSearchPresenter: CommandSearchPresenter

  init {
    val editorTextField = createPromptTextField(session)
    editor = editorTextField.getEditor(true) as EditorImpl
    controller = TerminalPromptController(editor, session, commandExecutor)
    controller.addListener(this)

    promptComponent = createPromptComponent()

    commandHistoryPresenter = CommandHistoryPresenter(project, editor, commandExecutor)
    commandSearchPresenter = CommandSearchPresenter(project, editor)

    val innerBorder = JBUI.Borders.empty(TerminalUi.promptTopInset,
                                         TerminalUi.blockLeftInset + TerminalUi.cornerToBlockInset,
                                         TerminalUi.promptBottomInset,
                                         TerminalUi.blockRightInset + TerminalUi.cornerToBlockInset)
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
    component.add(promptComponent)
    component.add(editorTextField)

    // move focus to the prompt text field on mouse click in the area of the prompt
    component.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true)
      }
    })
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      updatePrompt(controller.promptRenderingInfo)
    })
  }

  override fun promptContentUpdated(renderingInfo: PromptRenderingInfo) {
    updatePrompt(renderingInfo)
  }

  private fun updatePrompt(renderingInfo: PromptRenderingInfo) {
    val changePrompt = {
      promptComponent.clear()
      promptComponent.setContent(renderingInfo)
      promptComponent.revalidate()
      promptComponent.repaint()
    }
    promptComponent.change(changePrompt, false)
  }

  private fun SimpleColoredComponent.setContent(renderingInfo: PromptRenderingInfo) {
    var curOffset = 0
    for (highlighting in renderingInfo.highlightings) {
      if (curOffset < highlighting.startOffset) {
        val textPart = renderingInfo.text.substring(curOffset, highlighting.startOffset)
        append(textPart)
      }
      val textPart = renderingInfo.text.substring(highlighting.startOffset, highlighting.endOffset)
      val attributes = SimpleTextAttributes.fromTextAttributes(highlighting.textAttributesProvider.getTextAttributes())
      append(textPart, attributes)
      curOffset = highlighting.endOffset
    }
    if (curOffset < renderingInfo.text.length) {
      val textPart = renderingInfo.text.substring(curOffset)
      append(textPart)
    }
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
    val language = TerminalShellSupport.findByShellType(session.shellIntegration.shellType)?.promptLanguage
                   ?: PlainTextLanguage.INSTANCE
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
    editor.useTerminalDefaultBackground(this)
    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = 1.0f
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

  private fun createPromptComponent(): SimpleColoredComponent {
    val component = object : SimpleColoredComponent() {
      override fun updateUI() {
        super.updateUI()
        font = EditorUtil.getEditorFont()
      }
    }
    component.background = TerminalUi.defaultBackground(editor)
    component.foreground = TerminalUi.defaultForeground(editor)
    component.myBorder = JBUI.Borders.emptyBottom(2)
    component.ipad = JBInsets.emptyInsets()
    return component
  }

  override fun dispose() {}
}