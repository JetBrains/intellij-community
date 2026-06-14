// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.docstings

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.idea.TestFor
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.documentation.docstrings.DocStringParser
import com.jetbrains.python.documentation.docstrings.DocStringUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

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
      .firstOrNull { it.language.`is`(PythonLanguage.INSTANCE) }
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

  // PY-84818: a Markdown fenced code block in a docstring should be injected as Python in the source.
  fun `test markdown code-block is injected`() {
    val fileContent = """
          def foo():
              '''
              Doc.

              ```py
              def hello():
                  x = 1

                  print("world")
              ```

              More text.
              '''
              pass
        """.trimIndent()

    val injection = singlePythonInjection("md.py", fileContent)
    assertTrue("Injected text should contain the fenced code, but was:\n'${injection.text}'",
               injection.text.contains("def hello():") &&
               injection.text.contains("x = 1") &&
               injection.text.contains("print(\"world\")"))
  }

  fun `test markdown code-block with python alias is injected`() {
    val fileContent = """
          def foo():
              '''
              ```python3
              def hello(): ...
              ```
              '''
              pass
        """.trimIndent()

    val injection = singlePythonInjection("md3.py", fileContent)
    assertTrue("Injected text should contain the fenced code, but was:\n'${injection.text}'",
               injection.text.contains("def hello(): ..."))
  }

  // PY-84818: a docstring may put its summary on the same physical line as the opening quotes, so the first
  // content line has no indentation. String.trimIndent() would then see a common indent of 0 and leave the
  // fence indented (hence unrecognized); the PEP 257 dedent ignores the first line and handles this style.
  fun `test markdown code-block after a summary line is injected`() {
    val fileContent = """
          def foo():
              '''Summary line.

              ```py
              def hello(): ...
              ```
              '''
              pass
        """.trimIndent()

    val injection = singlePythonInjection("summary.py", fileContent)
    assertTrue("Injected text should contain the fenced code, but was:\n'${injection.text}'",
               injection.text.contains("def hello(): ..."))
  }

  fun `test markdown code-block with non-python language is not injected as python`() {
    val fileContent = """
          def foo():
              '''
              ```js
              const x = 1;
              ```
              '''
              pass
        """.trimIndent()

    myFixture.configureByText("nonpy.py", fileContent)
    val doc = getDocstringOfFunction("foo") ?: error("Docstring not found")
    val ilm = InjectedLanguageManager.getInstance(myFixture.project)
    val pyInjections = (ilm.getInjectedPsiFiles(doc) ?: emptyList())
      .mapNotNull { it.first as? PsiFile }
      .filter { it.language.`is`(PythonLanguage.INSTANCE) }
    assertTrue("Non-Python fence must not be injected as Python, but found: " +
               pyInjections.joinToString("\n---\n") { "'${it.text}'" }, pyInjections.isEmpty())
  }

  fun `test inspections are disabled for markdown code block`() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.configureByText("a.py", """
        def foo():
            '''
            ```py
            completely_unresolved_name
            ```
            '''
            pass
    """.trimIndent())
    myFixture.checkHighlighting(false, false, false) // no warnings expected
  }

  @TestFor(issues = ["PY-84818"])
  fun `test large markdown code-block is injected without freezing`() {
    val text = buildString {
      append("""
        def foo():
            '''
            ```py
        
        """.trimIndent())
      repeat(10_000) {
        append("    value_").append(it).append(" = ").append(it).append('\n')
      }
      append("""
        |    ```
        |    '''
        |    pass
        """.trimMargin())
    }

    myFixture.configureByText("big.py", text)
    val doc = getDocstringOfFunction("foo") ?: error("Docstring not found")
    val ilm = InjectedLanguageManager.getInstance(myFixture.project)

    val (injected, duration) = measureTimedValue {
      ilm.getInjectedPsiFiles(doc) ?: error("No injected PSI files found")
    }

    val pyInjections = injected
      .mapNotNull { it.first as? PsiFile }
      .filter { it.language.`is`(PythonLanguage.INSTANCE) }
    assertTrue("Expected the 10K-line fenced code to be injected as Python", pyInjections.isNotEmpty())
    assertTrue("Injection should span the whole fence, down to the last line",
               pyInjections.any { it.text.contains("value_9999 = 9999") })
    // Coarse upper bound: a regression to super-linear scanning would blow far past this on 10K lines.
    assertTrue("Injecting a 10K-line Markdown fence took ${duration.inWholeMilliseconds}ms",
               duration < 1.seconds)
  }

  private fun singlePythonInjection(fileName: String, fileContent: String): PsiFile {
    myFixture.configureByText(fileName, fileContent)
    val doc = getDocstringOfFunction("foo") ?: error("Docstring not found")
    val ilm = InjectedLanguageManager.getInstance(myFixture.project)
    val injected = ilm.getInjectedPsiFiles(doc) ?: error("No injected PSI files found")
    val pyInjections = injected
      .mapNotNull { it.first as? PsiFile }
      .filter { it.language.`is`(PythonLanguage.INSTANCE) }
    assertTrue("Expected a Python injection from the Markdown fence", pyInjections.isNotEmpty())
    return pyInjections.first()
  }

  private fun testCodeBlockInjection(fileName: String, fileContent: String, expectedInjectedText: String, language: Language = PythonLanguage.INSTANCE) {
    myFixture.configureByText(fileName, fileContent)

    val doc = getDocstringOfFunction("foo") ?: error("Docstring not found")
    val fmt = DocStringParser.guessDocStringFormat(doc.stringValue.trim(), doc)
    assertEquals("Docstring format should be detected as reST", DocStringFormat.REST, fmt)

    val ilm = InjectedLanguageManager.getInstance(myFixture.project)
    val injected = ilm.getInjectedPsiFiles(doc) ?: error("No injected PSI files found")
    assertTrue("Expected at least one injected PSI file", injected.isNotEmpty())

    val codeBlockInjections = injected
      .mapNotNull { it.first as? PsiFile }
      .filter { it.language.`is`(language) }

    assertTrue("Expected at least one injection from code-block", codeBlockInjections.isNotEmpty())

    val matchingInjection = codeBlockInjections.firstOrNull { it.text == expectedInjectedText }
    assertNotNull(
      "Expected to find the injection with text:\n'$expectedInjectedText'\n\nBut found:\n${codeBlockInjections.joinToString("\n---\n") { "'${it.text}'" }}",
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

  fun `test inspections are disabled for code block`() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.configureByText("a.py", """
        def foo():
            '''
            .. code-block:: python

                completely_unresolved_name
            '''
            pass
    """.trimIndent())
    myFixture.checkHighlighting(false, false, false) // no warnings expected
  }

  fun `test inspections are enabled for doctest`() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.configureByText("a.py", """
        def foo():
            '''
            >>> <warning descr="Unresolved reference 'completely_unresolved_name'">completely_unresolved_name</warning>
            '''
            pass
    """.trimIndent())
    myFixture.checkHighlighting(true, false, true)
  }
}