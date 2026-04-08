package com.jetbrains.python.documentation.docstings

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.documentation.docstrings.DocStringParser
import com.jetbrains.python.documentation.docstrings.DocStringUtil
import com.jetbrains.python.documentation.doctest.PyDocstringLanguageDialect
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression

class PyRestCodeBlockInjectionTest : PyTestCase() {

  fun `test rest code-block highlighting`() {
    myFixture.configureByText(
      "c.py",
      """
        def spam():
            '''
            Doc.

            .. code-block:: python

               def eggs():
                   if True:
                       print("ok")
            '''
            pass
      """.trimIndent()
    )

    val doc = getDocstringOfFunction("spam") ?: error("Docstring not found")

    val raw = doc.stringValue
    val fmt = DocStringParser.guessDocStringFormat(raw.trim(), doc)
    assertEquals(DocStringFormat.REST, fmt)

    val ilm = InjectedLanguageManager.getInstance(myFixture.project)
    @Suppress("UNCHECKED_CAST")
    val injected: List<Pair<PsiElement, *>>? = ilm.getInjectedPsiFiles(doc)
    assertNotNull("No injected PSI files", injected)
    val pyInjectedFile = injected!!
      .map { it.first }
      .filterIsInstance<PsiFile>()
      .firstOrNull { it.language is PyDocstringLanguageDialect }
      ?: error("Python docstring language injection not found")

    val highlightInfos: List<HighlightInfo> =
      CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.file, myFixture.editor, intArrayOf(), true)

    val injectedRanges = injected
      .filter { it.first == pyInjectedFile }
      .mapNotNull { it.second as? com.intellij.openapi.util.TextRange }

    assertTrue("Injected ranges are empty", injectedRanges.isNotEmpty())

    val hasInfoInsideInjected = highlightInfos.any { info ->
      val start = info.startOffset
      injectedRanges.any { it.containsOffset(start) }
    }
    assertTrue("Expected highlighting infos inside injected code-block", hasInfoInsideInjected)

    val containsKeywordInfo = highlightInfos.any { info ->
      val text = myFixture.file.text.substring(info.startOffset, info.endOffset.coerceAtMost(myFixture.file.textLength))
      (text == "def" || text == "if") && injectedRanges.any { it.containsOffset(info.startOffset) }
    }
    assertTrue("Expected Python keyword highlighting inside injected code-block", containsKeywordInfo)
  }

  fun `test rest code-block is injected and preserves newlines`() {
    val fileContent = """
          def foo():
              '''
              Doc.

              .. code-block:: python

                 def hello():
                     x = 1

                     print("world")

              More text.
              '''
              pass
        """.trimIndent()

    testCodeBlockInjection("a.py", fileContent, "       def hello():\n           x = 1\n\n           print(\"world\")\n")
  }

  fun `test extended rest code-block with options is injected and preserves newlines`() {
    val fileContent = """
          def foo():
              '''
              Doc.

              .. code-block:: python
                 :caption: Example Python Code
                 :linenos:
                 :lineno-start: 10
                 :emphasize-lines: 2-3
                 :dedent: 4

                 def hello():
                     x = 1

                     print("world")

              More text.
              '''
              pass
        """.trimIndent()

    testCodeBlockInjection("b.py", fileContent, "       def hello():\n           x = 1\n\n           print(\"world\")\n")
  }

  private fun testCodeBlockInjection(fileName: String, fileContent: String, expectedInjectedText: String) {
    myFixture.configureByText(fileName, fileContent)

    val doc = getDocstringOfFunction("foo") ?: error("Docstring not found")
    val fmt = DocStringParser.guessDocStringFormat(doc.stringValue.trim(), doc)
    assertEquals("Docstring format should be detected as reST", DocStringFormat.REST, fmt)

    val ilm = InjectedLanguageManager.getInstance(myFixture.project)
    val injected = ilm.getInjectedPsiFiles(doc) ?: error("No injected PSI files found")
    assertTrue("Expected at least one injected PSI file", injected.isNotEmpty())

    val doctestInjections = injected
      .mapNotNull { it.first as? PsiFile }
      .filter { it.language.id == "Doctest" }

    assertTrue("Expected at least one Doctest injection from code-block", doctestInjections.isNotEmpty())

    val matchingInjection = doctestInjections.firstOrNull { it.text == expectedInjectedText }
    assertNotNull(
      "Expected to find Doctest injection with text:\n'$expectedInjectedText'\n\nBut found:\n${doctestInjections.joinToString("\n---\n") { "'${it.text}'" }}",
      matchingInjection
    )
  }

  private fun getDocstringOfFunction(functionName: String): PyStringLiteralExpression? {
    val pyFile = myFixture.file as? PyFile ?: return null
    val fn: PyFunction = pyFile.topLevelFunctions.firstOrNull { it.name == functionName } ?: return null

    DocStringUtil.findDocStringExpression(fn)?.let { return it }

    val firstStmt = fn.statementList.statements.firstOrNull()
    if (firstStmt is PyExpressionStatement) {
      val expr = firstStmt.expression
      if (expr is PyStringLiteralExpression) return expr
    }
    return null
  }
}