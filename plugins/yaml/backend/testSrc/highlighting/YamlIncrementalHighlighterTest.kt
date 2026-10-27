// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.highlighting

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.yaml.YAMLSyntaxHighlighter
import org.junit.jupiter.api.Test

@TestApplication
class YamlIncrementalHighlighterTest {
  @Test
  fun `closing double quote after a restart point`() {
    doTestClosingQuoteAfterRestartPoint("\"")
  }

  @Test
  fun `closing single quote after a restart point`() {
    doTestClosingQuoteAfterRestartPoint("'")
  }

  /**
   * The empty line after `x: [ y ]` is a restart point of the lexer.
   * The test opens a quote before it and closes the quote after it.
   */
  private fun doTestClosingQuoteAfterRestartPoint(quote: String): Unit = timeoutRunBlocking {
    val text = "a:\n  b: c\nx: [ y ]\n\nd: e\n"
    withContext(Dispatchers.EDT) {
      val document = DocumentImpl(text)
      val editor = EditorFactory.getInstance().createEditor(document) as EditorEx
      try {
        editor.highlighter = LexerEditorHighlighter(YAMLSyntaxHighlighter(), EditorColorsManager.getInstance().globalScheme)
        CommandProcessor.getInstance().executeCommand(null, {
          ApplicationManager.getApplication().runWriteAction {
            document.insertString(text.indexOf('c'), quote)
            document.insertString(text.indexOf('e') + 2, quote)
          }
        }, null, null)
        CheckHighlighterConsistency.performCheck(editor)
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }
}
