// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Subsystems.Formatter
@Layers.Functional
@TestFor(classes = [PyTrailingCommasPostFormatProcessor::class], issues = ["PY-93902"])
class PyTrailingCommasPostFormatProcessorTest : PyCodeInsightTestCase() {

  @TestDisposable
  lateinit var testDisposable: Disposable

  @Test
  fun `nested trailing commas are reformatted in one pass`() {
    val counter = ElementPostFormatCounter()
    PostFormatProcessor.EP_NAME.point.registerExtension(counter, testDisposable)
    myFixture.configureByText(PythonFileType.INSTANCE, """
      call(
          [
              1,
              2
          ],
          {
              'a': 3
          }
      )
    """.trimIndent())

    val settings = CodeStyle.createTestSettings()
    settings.getCustomSettings(PyCodeStyleSettings::class.java).apply {
      USE_TRAILING_COMMA_IN_ARGUMENTS_LIST = true
      USE_TRAILING_COMMA_IN_COLLECTIONS = true
    }
    CodeStyle.doWithTemporarySettings(myFixture.project, settings, Runnable {
      WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
        CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, 0, myFixture.file.textLength)
      })
    })

    assertEquals("""
      call(
          [
              1,
              2,
          ],
          {
              'a': 3,
          },
      )
    """.trimIndent(), myFixture.file.text.trimEnd())
    assertEquals(1, counter.processedElements)
  }

  /**
   * Counts the element reformats that run the post-format processors.
   * The processor is whitespace-only, so also a whitespace-only reformat runs it.
   */
  private class ElementPostFormatCounter : PostFormatProcessor {
    var processedElements: Int = 0

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
      processedElements++
      return source
    }

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange = rangeToReformat

    override fun isWhitespaceOnly(): Boolean = true
  }
}
