// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

class TerminalPromptPanel(private val project: Project,
                          private val settings: JBTerminalSystemSettingsProviderBase,
                          private val commandExecutor: TerminalCommandExecutor) : JPanel(), ComponentContainer {
  private val document: Document
  private val editor: EditorImpl
  private val completionProvider: TerminalCompletionProvider = NewTerminalSessionCompletionProvider(project)

  init {
    document = DocumentImpl("", true)
    editor = TerminalUiUtils.createEditor(document, project, settings)
    editor.putUserData(KEY, this)  // to access this panel from editor action handlers
    Disposer.register(this, editor.disposable)

    val innerBorder = JBUI.Borders.customLine(UIUtil.getTextFieldBackground(), 6, 0, 6, 0)
    val outerBorder = JBUI.Borders.customLineTop(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
    border = JBUI.Borders.compound(outerBorder, innerBorder)

    layout = BorderLayout()
    add(editor.component, BorderLayout.CENTER)
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

  override fun getPreferredSize(): Dimension {
    val baseSize = super.getPreferredSize()
    JBInsets.addTo(baseSize, insets)
    val lineCount = max(editor.document.lineCount, 1)
    return Dimension(baseSize.width, lineCount * editor.lineHeight + insets.top + insets.bottom)
  }

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent = editor.contentComponent

  override fun dispose() {

  }

  companion object {
    val KEY: Key<TerminalPromptPanel> = Key.create("TerminalPromptPanel")
  }
}