// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing

import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.validation.PySyntaxAnnotator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines
import kotlin.io.path.readText

/**
 * Compares the parser and the annotators with CPython on a corpus of small snippets.
 *
 * `cpythonSyntaxDiff/snippets` holds one snippet per `.py` file. `cpythonSyntaxDiff/cpython.tsv` holds the result
 * of `compile(snippet, "<p>", "exec")` for each snippet, with one column for each CPython version. A result that starts
 * with `OK` means that CPython compiles the snippet. `cpythonSyntaxDiff/update_cpython.py` writes the column of the CPython
 * that runs it.
 *
 * The PyCharm verdict for a snippet is `parse error` (the tree has a [PsiErrorElement]), `annotator error`
 * (an annotator gives an ERROR) or `clean`. A WARNING and an ERROR of an inspection count as `clean`.
 *
 * `cpythonSyntaxDiff/baseline.tsv` holds each known disagreement. The test fails when a disagreement appears,
 * disappears or changes its PyCharm verdict. The failure message gives the lines to add to or to remove from the baseline.
 */
@TestFor(classes = [PythonParser::class, PySyntaxAnnotator::class])
@Subsystems.Parsing
@Layers.Functional
class PyCPythonSyntaxDifferentialTest : PyCodeInsightTestCase() {

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON313)
  fun `python 3_13`() = checkLevel("3.13")

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON314)
  fun `python 3_14`() = checkLevel("3.14")

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON315)
  fun `python 3_15`() = checkLevel("3.15")

  private fun checkLevel(level: String) {
    val snippets = data("snippets").listDirectoryEntries("*.py").associateBy { it.nameWithoutExtension }
    val results = cpythonResults(level)
    Assertions.assertEquals(snippets.keys.sorted(), results.keys.sorted(), "Run update_cpython.py after a change of the snippets.")

    val actual = sortedSetOf<String>()
    for ((id, path) in snippets) {
      val cpythonOk = results.getValue(id).startsWith("OK")
      val verdict = pycharmVerdict(path.readText(), level)
      if ((verdict == CLEAN) != cpythonOk) {
        actual += listOf(id, level, if (cpythonOk) "OK" else "error", verdict).joinToString("\t")
      }
    }
    val expected = dataLines("baseline.tsv").filter { it.split('\t')[1] == level }.toSortedSet()

    val message = buildString {
      append("Disagreements with CPython $level changed. Update cpythonSyntaxDiff/baseline.tsv.")
      (actual - expected).forEach { append("\nadd:    ").append(it) }
      (expected - actual).forEach { append("\nremove: ").append(it) }
    }
    Assertions.assertEquals(expected, actual, message)
  }

  private fun cpythonResults(level: String): Map<String, String> {
    val lines = data("cpython.tsv").readLines().filter { it.isNotEmpty() }
    val column = lines.first().split('\t').indexOfFirst { it.startsWith("CPython $level.") }
    Assertions.assertTrue(column > 0, "cpython.tsv has no column for CPython $level. Run update_cpython.py with CPython $level.")
    val results = lines.drop(1).map { it.split('\t') }.associate { it[0] to it.getOrElse(column) { "" } }
    val missing = results.filterValues { it.isBlank() }.keys
    Assertions.assertTrue(missing.isEmpty(), "cpython.tsv has no CPython $level result for $missing. Run update_cpython.py with CPython $level.")
    return results
  }

  private fun pycharmVerdict(code: String, level: String): String {
    val file = myFixture.configureByText("snippet.py", code)
    Assertions.assertEquals(level, runReadActionBlocking { LanguageLevel.forElement(file) }.toPythonVersion())
    val highlights = myFixture.doHighlighting()
    val hasParseError = runReadActionBlocking { PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null }
    return when {
      hasParseError -> "parse error"
      highlights.any { it.severity == HighlightSeverity.ERROR && it.inspectionToolId == null } -> "annotator error"
      else -> CLEAN
    }
  }

  private fun dataLines(name: String): List<String> =
    data(name).readLines().filter { it.isNotEmpty() && !it.startsWith("# ") }

  private fun data(name: String): Path = Path.of(PythonTestUtil.getTestDataPath(), "cpythonSyntaxDiff", name)
}

private const val CLEAN = "clean"
