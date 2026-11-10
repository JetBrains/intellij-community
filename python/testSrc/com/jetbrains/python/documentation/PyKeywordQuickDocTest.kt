// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.intellij.idea.TestFor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PythonDialectsTokenSetProvider
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@TestFor(issues = ["PY-93392"], classes = [PythonDocumentationProvider::class])
@Subsystems.QuickDocumentation
@Layers.Functional
class PyKeywordQuickDocTest : PyCodeInsightTestCase() {

  @Test
  fun `keyword with a description shows the description`() = runInEdtAndWait {
    val doc = keywordDocAtCaret("""
      def f():
          return 1
      #     └ CARET
    """, "return")
    assertNotNull(doc)
    assertTrue(plainText(doc).startsWith("The return statement"), doc)
  }

  @Test
  fun `async keyword without a description shows no documentation`() = runInEdtAndWait {
    assertNull(keywordDocAtCaret("""
      async def f():
      # └ CARET
          pass
    """, "async"))
  }

  @Test
  fun `await keyword without a description shows no documentation`() = runInEdtAndWait {
    assertNull(keywordDocAtCaret("""
      async def f(g):
          await g()
      #     └ CARET
    """, "await"))
  }

  @Test
  fun `match keyword without a description shows no documentation`() = runInEdtAndWait {
    assertNull(keywordDocAtCaret("""
      match x:
      # └ CARET
          case 1:
              pass
    """, "match"))
  }

  @Test
  fun `case keyword without a description shows no documentation`() = runInEdtAndWait {
    assertNull(keywordDocAtCaret("""
      match x:
          case 1:
      #     └ CARET
              pass
    """, "case"))
  }

  private fun keywordDocAtCaret(@Language("Python") code: String, keyword: String): String? {
    configureWithCaret(code)
    val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
    assertEquals(keyword, element.text)
    assertTrue(PythonDialectsTokenSetProvider.getInstance().keywordTokens.contains(element.node.elementType))
    return PythonDocumentationProvider().generateDoc(element, element)
  }

  private fun plainText(html: String): String =
    StringUtil.unescapeXmlEntities(StringUtil.removeHtmlTags(html)).trim()
}
