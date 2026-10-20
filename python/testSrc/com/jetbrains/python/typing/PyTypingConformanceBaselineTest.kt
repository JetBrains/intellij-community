// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.typing

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.isControlFlowException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.tools.PyConformanceInspections
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The typing conformance suite on the mock SDK, compared with a baseline of today's results.
 *
 * For each file of `testData/typing/conformance/tests`, the baseline file `testData/typing/baseline/<name>.txt` lists the
 * required lines without a highlight (`missed`), the error groups without the expected highlights (`group`), each
 * highlight on a line without an expectation (`unexpected`, with the inspection id), and each logged error or exception
 * of the highlighting (`exception`). A file without such rows has no baseline file. `_scores.txt` gives the result of
 * each chapter of the suite.
 *
 * The test fails on a difference in either direction, so a fix and a regression both change a baseline file.
 * Run it with `-Dpass.idea.tests.overwrite.data=true` to write the new baseline.
 *
 * The expectations use the comment syntax of the suite: `# E`, `# E?` for an optional error, `# E: <message>`, and
 * `# E[tag]` for a group of lines where one line has the error (`# E[tag+]`: one or more lines).
 * A highlight of severity WARNING or higher counts.
 */
@TestFor(issues = ["PY-76770", "PY-92814"])
@Subsystems.Typing
@Layers.Functional
class PyTypingConformanceBaselineTest : PyCodeInsightTestCase() {

  override val defaultInspections: Set<Class<out LocalInspectionTool>> = PyConformanceInspections.classes.toSet()

  private val suiteRoot = Path.of(PythonTestUtil.getTestDataPath(), SUITE_DIR)
  private val baselineRoot = Path.of(PythonTestUtil.getTestDataPath(), BASELINE_DIR)

  @TestFactory
  fun conformance(): List<DynamicTest> {
    val files = Files.walk(suiteRoot).use { paths ->
      paths.filter { it.extension in SUITE_EXTENSIONS && !it.name.startsWith("_") }
        .map { suiteRoot.relativize(it).toString() }
        .sorted()
        .toList()
    }
    val scores = LinkedHashMap<String, FileScore>()
    val fileTests = files.map { path ->
      DynamicTest.dynamicTest(path) {
        if (myFixture.findFileInTempDir(path) == null) myFixture.copyDirectoryToProject(SUITE_DIR, "")
        val result = checkFile(path)
        scores[path] = result.score
        compareWithFile(baselineRoot.resolve(baselineName(path)), result.rows)
      }
    }
    val scoreTest = DynamicTest.dynamicTest(SCORES_FILE) {
      Assertions.assertEquals(files.size, scores.size, "Run all conformance files before the score")
      compareWithFile(baselineRoot.resolve(SCORES_FILE), renderScores(scores))
    }
    val obsoleteTest = DynamicTest.dynamicTest("obsolete baseline files") {
      val known = files.mapTo(hashSetOf(SCORES_FILE)) { baselineName(it) }
      val obsolete = Files.walk(baselineRoot).use { paths ->
        paths.filter { Files.isRegularFile(it) && baselineRoot.relativize(it).toString() !in known }.sorted().toList()
      }
      if (UsefulTestCase.OVERWRITE_TESTDATA) obsolete.forEach { it.deleteIfExists() }
      else Assertions.assertEquals(emptyList<Path>(), obsolete, "Baseline files without a conformance file")
    }
    return fileTests + scoreTest + obsoleteTest
  }

  private fun baselineName(path: String): String = path.substringBeforeLast('.') + ".txt"

  private class FileScore(val required: Int, val met: Int, val unexpected: Int)

  private class FileResult(val rows: List<String>, val score: FileScore)

  private class Expectations {
    /** The required and the optional error lines. A value is the expected message or an empty string. */
    val required = LinkedHashMap<Int, String>()
    val optional = HashSet<Int>()

    /** The lines of each error group, and whether the group accepts several errors. */
    val groups = LinkedHashMap<String, Pair<MutableList<Int>, Boolean>>()
  }

  private fun checkFile(path: String): FileResult {
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(path)!!)
    val expectations = runReadActionBlocking { readExpectations() }
    val exceptions = CopyOnWriteArrayList<String>()
    var highlights: List<HighlightInfo> = emptyList()
    val collector = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        if (t != null && t.isControlFlowException) return super.processError(category, message, details, t)
        exceptions += "exception logged: ${firstLine(t?.message ?: message)}"
        return Action.NONE
      }
    }
    withInspections {
      LoggedErrorProcessor.executeWith<RuntimeException>(collector) {
        try {
          highlights = myFixture.doHighlighting(HighlightSeverity.WARNING)
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          exceptions += "exception ${e.javaClass.simpleName}: ${firstLine(e.message.orEmpty())}"
        }
      }
    }
    val document = myFixture.editor.document
    val byLine = runReadActionBlocking { highlights.groupBy { document.getLineNumber(it.startOffset) } }

    val rows = mutableListOf<Pair<Int, String>>()
    val met = expectations.required.keys.count { it in byLine }
    for ((line, message) in expectations.required) {
      if (line !in byLine) rows += line to "missed ${line + 1}" + (if (message.isEmpty()) "" else ": $message")
    }
    for ((tag, group) in expectations.groups) {
      val (lines, allowsSeveral) = group
      val count = lines.count { it in byLine }
      if (count == 0) rows += lines.first() to "group $tag at ${lines.first() + 1}: no error"
      else if (count > 1 && !allowsSeveral) rows += lines.first() to "group $tag at ${lines.first() + 1}: $count errors"
    }
    val expectedLines = expectations.required.keys + expectations.optional + expectations.groups.values.flatMap { it.first }
    var unexpected = 0
    for ((line, infos) in byLine.toSortedMap()) {
      if (line in expectedLines) continue
      for (info in infos.sortedWith(compareBy({ it.inspectionToolId.orEmpty() }, { it.description.orEmpty() }))) {
        rows += line to "unexpected ${line + 1}: ${toolId(info)}: ${info.description}"
        unexpected++
      }
    }
    val sorted = rows.sortedWith(compareBy({ it.first }, { it.second })).map { it.second }
    return FileResult(exceptions.distinct().sorted() + sorted, FileScore(expectations.required.size, met, unexpected))
  }

  private fun firstLine(text: String): String = text.lineSequence().firstOrNull().orEmpty()

  private fun toolId(info: HighlightInfo): String = info.inspectionToolId ?: "annotator"

  private fun readExpectations(): Expectations {
    val expectations = Expectations()
    val document = myFixture.editor.document
    for (comment in PsiTreeUtil.findChildrenOfType(myFixture.file, PsiComment::class.java)) {
      val start = comment.textRange.startOffset
      val line = document.getLineNumber(start)
      if (document.getText(TextRange(document.getLineStartOffset(line), start)).isBlank()) continue
      val match = EXPECTATION.find(comment.text) ?: continue
      val (tag, optional, message) = match.destructured
      when {
        tag.isNotEmpty() -> {
          val name = tag.removeSuffix("+")
          expectations.groups.getOrPut(name) { mutableListOf<Int>() to tag.endsWith("+") }.first += line
        }
        optional.isNotEmpty() -> expectations.optional += line
        else -> expectations.required[line] = message.trim()
      }
    }
    return expectations
  }

  private fun renderScores(scores: Map<String, FileScore>): List<String> {
    val chapters = scores.entries.groupBy({ it.key.substringBefore('_') }, { it.value }).toSortedMap()
    val rows = chapters.map { (chapter, files) ->
      "$chapter: ${files.sumOf { it.met }}/${files.sumOf { it.required }} required lines met, ${files.sumOf { it.unexpected }} unexpected"
    }
    val all = scores.values
    return rows + "total: ${all.sumOf { it.met }}/${all.sumOf { it.required }} required lines met, ${all.sumOf { it.unexpected }} unexpected"
  }

  /** Compares [rows] with [file]. No rows means no file. With the overwrite switch, writes the rows into the file. */
  private fun compareWithFile(file: Path, rows: List<String>) {
    val actual = if (rows.isEmpty()) null else rows.joinToString("\n", postfix = "\n")
    val expected = if (file.exists()) file.readText() else null
    if (actual == expected) return
    if (UsefulTestCase.OVERWRITE_TESTDATA) {
      if (actual == null) file.deleteIfExists()
      else {
        Files.createDirectories(file.parent)
        file.writeText(actual)
      }
      return
    }
    Assertions.assertEquals(expected ?: "<no baseline file>", actual ?: "<no rows>", "The conformance result differs from $file")
  }

  private companion object {
    const val SUITE_DIR = "typing/conformance/tests"
    const val BASELINE_DIR = "typing/baseline"
    const val SCORES_FILE = "_scores.txt"
    val SUITE_EXTENSIONS = setOf("py", "pyi")

    /** `# E`, `# E?`, `# E: message`, `# E[tag]` and `# E[tag+]` at the start of a comment. */
    val EXPECTATION = Regex("""^#\s*E(?:\[([^]\n]+)]|(\?))?(?::(.*))?(?:\s|$)""")
  }
}
