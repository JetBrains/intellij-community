// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class TerminalPromptPanel(private val project: Project,
                          private val commandExecutor: TerminalCommandExecutor) : JPanel(), ComponentContainer {
  private val document: Document
  private val editor: EditorImpl
  private val completionProvider: TerminalCompletionProvider = NewTerminalSessionCompletionProvider(project)

  init {
    document = DocumentImpl("", true)
    editor = createEditor(document)
    editor.putUserData(KEY, this)  // to access this panel from editor action handlers
    Disposer.register(this, editor.disposable)

    layout = BorderLayout()
    add(editor.component, BorderLayout.CENTER)
  }

  private fun createEditor(document: Document): EditorImpl {
    val editor = EditorFactory.getInstance().createEditor(document, project, EditorKind.CONSOLE) as EditorImpl
    editor.isScrollToCaret = false
    editor.setCustomCursor(this, Cursor.getDefaultCursor())
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    editor.gutterComponentEx.isPaintBackground = false
    editor.settings.apply {
      isShowingSpecialChars = false
      isLineNumbersShown = false
      setGutterIconsShown(false)
      isRightMarginShown = false
      isFoldingOutlineShown = false
      isCaretRowShown = false
      additionalLinesCount = 0
      additionalColumnsCount = 0
      isBlockCursor = true
    }
    return editor
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

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent = editor.contentComponent

  override fun dispose() {

  }

  companion object {
    val KEY: Key<TerminalPromptPanel> = Key.create("TerminalPromptPanel")
  }
}