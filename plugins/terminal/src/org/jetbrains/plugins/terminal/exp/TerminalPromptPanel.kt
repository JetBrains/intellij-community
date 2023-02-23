// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.PaintingParent.Wrapper
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import java.awt.Color
import java.awt.Dimension
import javax.swing.*
import kotlin.math.max

class TerminalPromptPanel(private val project: Project,
                          settings: JBTerminalSystemSettingsProviderBase,
                          session: TerminalSession,
                          private val commandExecutor: TerminalCommandExecutor) : JPanel(), ComponentContainer, ShellCommandListener {
  private val document: Document
  private val editor: EditorImpl

  private val promptLabel: JLabel

  private val completionProvider: TerminalCompletionProvider = NewTerminalSessionCompletionProvider(project)

  val charSize: Dimension
    get() = Dimension(editor.charHeight, editor.lineHeight)

  init {
    document = DocumentImpl("", true)
    editor = TerminalUiUtils.createEditor(document, project, settings)
    editor.putUserData(KEY, this)  // to access this panel from editor action handlers
    Disposer.register(this, editor.disposable)

    val innerBorder = JBUI.Borders.customLine(UIUtil.getTextFieldBackground(), 6, 0, 6, 0)
    val outerBorder = JBUI.Borders.customLineTop(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
    border = JBUI.Borders.compound(outerBorder, innerBorder)

    promptLabel = createPromptLabel()
    promptLabel.text = computePromptText(TerminalProjectOptionsProvider.getInstance(project).startingDirectory ?: "")

    session.addCommandListener(this, parentDisposable = this)

    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(promptLabel)
    add(Box.createRigidArea(JBDimension(0, 4)))

    val editorWrapper = object : Wrapper(editor.component) {
      override fun getPreferredSize(): Dimension {
        val baseSize = super.getPreferredSize()
        JBInsets.addTo(baseSize, insets)
        val lineCount = max(editor.document.lineCount, 1)
        return Dimension(baseSize.width, lineCount * editor.lineHeight + insets.top + insets.bottom)
      }
    }
    editorWrapper.alignmentX = JComponent.LEFT_ALIGNMENT
    add(editorWrapper)
  }

  private fun createPromptLabel(): JLabel {
    val label = JLabel()
    label.font = EditorUtil.getEditorFont()
    label.border = JBUI.Borders.emptyLeft(7)
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

  fun reset() {
    document.setText("")
  }

  fun handleEnterPressed() {
    commandExecutor.startCommandExecution(document.text)
  }

  fun handleTabPressed(offset: Int) {
    val command = document.getText(TextRange(0, offset))
    ApplicationManager.getApplication().executeOnPooledThread {
      completionProvider.getCompletionItems(command).thenAccept { items ->
        if (items.isNotEmpty()) {
          invokeLater { completionProposed(items) }
        }
      }
    }
  }

  private fun completionProposed(items: List<String>) {
    val offset = editor.caretModel.offset
    if (items.size == 1) {
      val addedPart = items.first()
      document.insertString(offset, addedPart)
      editor.caretModel.moveToOffset(offset + addedPart.length)
    }
    else {
      val typedPart = findCommonPrefix(items)
      val lookupItems = items.map { LookupElementBuilder.create(it) }.toTypedArray()
      LookupManager.getInstance(project).showLookup(editor, lookupItems, typedPart)
    }
  }

  private fun findCommonPrefix(items: List<String>): String {
    if (items.isEmpty()) throw IllegalArgumentException("Provided list is empty")

    val prefix = StringBuilder()
    val firstStr = items.first()
    for ((ind, ch) in firstStr.withIndex()) {
      for (str in items.subList(1, items.size)) {
        if (str.length == ind || str[ind] != ch) {
          return prefix.toString()
        }
      }
      prefix.append(ch)
    }
    return prefix.toString()
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
  }
}