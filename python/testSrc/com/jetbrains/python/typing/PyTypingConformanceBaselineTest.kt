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
import org.junit.jupiter.api.Test
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
 * A trailing ` # ` comment may follow a baseline row, and a comment starting with `expected` marks a matching file as expected in the
 * summary. A line that holds only a comment belongs to the row below it, or to the end of the file.
 *
 * The test fails on a difference in either direction, so a fix and a regression both change a baseline file.
 * Run it with `-Dpass.idea.tests.overwrite.data=true` to write the new baseline.
 *
 * The expectations use the comment syntax of the suite: `# E`, `# E?` for an optional error, `# E: <message>`, and
 * `# E[tag]` for a group of lines where one line has the error (`# E[tag+]`: one or more lines).
 * A highlight of severity WARNING or higher counts.
 */
@TestFor(issues = ["PY-76770", "PY-92814", "PY-76863"])
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
    val statuses = LinkedHashMap<String, FileStatus>()
    val fileTests = files.map { path ->
      DynamicTest.dynamicTest(path) {
        if (myFixture.findFileInTempDir(path) == null) myFixture.copyDirectoryToProject(SUITE_DIR, "")
        val result = checkFile(path)
        scores[path] = result.score
        val file = baselineRoot.resolve(baselineName(path))
        val baseline = readBaseline(file)
        statuses[path] = fileStatus(baseline, result.rows)
        compareWithFile(file, baseline, result.rows)
      }
    }
    val scoreTest = DynamicTest.dynamicTest(SCORES_FILE) {
      Assertions.assertEquals(files.size, scores.size, "Run all conformance files before the score")
      val file = baselineRoot.resolve(SCORES_FILE)
      compareWithFile(file, readBaseline(file), renderScores(scores))
    }
    val obsoleteTest = DynamicTest.dynamicTest("obsolete baseline files") {
      val known = files.mapTo(hashSetOf(SCORES_FILE)) { baselineName(it) }
      val obsolete = Files.walk(baselineRoot).use { paths ->
        paths.filter { Files.isRegularFile(it) && baselineRoot.relativize(it).toString() !in known }.sorted().toList()
      }
      if (UsefulTestCase.OVERWRITE_TESTDATA) obsolete.forEach { it.deleteIfExists() }
      else Assertions.assertEquals(emptyList<Path>(), obsolete, "Baseline files without a conformance file")
    }
    val summaryTest = DynamicTest.dynamicTest("summary") {
      println(renderSummary(files.size, statuses.values))
    }
    return buildList {
      addAll(fileTests)
      add(scoreTest)
      add(obsoleteTest)
      add(summaryTest)
    }
  }

  @Test
  fun `baseline row text with hash`() {
    val row = "unexpected 1: tool: description #type"
    Assertions.assertEquals(row, parseBaselineLine(row)?.row)
  }

  @Test
  fun `expected baseline comment`() {
    val row = parseBaselineLine("missed 1 # expected, see PY-89651")
    Assertions.assertEquals("missed 1", row?.row)
    Assertions.assertEquals("expected, see PY-89651", row?.comment)
    Assertions.assertTrue(row?.isExpected == true)
  }

  @Test
  fun `baseline file without rows`() {
    Assertions.assertNull(parseBaseline("# comment\n\n"))
  }

  @Test
  fun `baseline comment lines`() {
    val baseline = parseBaseline("# header\n\nmissed 1\n# between\nmissed 2 # expected\n# tail\n")!!
    Assertions.assertEquals(listOf("missed 1", "missed 2"), baseline.rows.map { it.row })
    Assertions.assertEquals(listOf("# header"), baseline.rows[0].commentLines)
    Assertions.assertEquals(listOf("# between"), baseline.rows[1].commentLines)
    Assertions.assertEquals(listOf("# tail"), baseline.trailingLines)
  }

  @Test
  fun `overwrite keeps comment lines`() {
    val baseline = parseBaseline("# header\nmissed 1 # expected\n# gone\nmissed 2\n# tail\n")
    Assertions.assertEquals(
      listOf("# header", "missed 1 # expected", "missed 3", "# gone", "# tail"),
      renderBaseline(baseline, listOf("missed 1", "missed 3")),
    )
  }

  private fun baselineName(path: String): String = path.substringBeforeLast('.') + ".txt"

  private class FileScore(val required: Int, val met: Int, val unexpected: Int)

  private class FileResult(val rows: List<String>, val score: FileScore)

  /** The outcome of a conformance file for the console summary. */
  private enum class FileStatus {
    /** No baseline file: the result has no rows. */
    PASSED,

    /** The result matches a baseline where every row has a comment starting with `expected`. */
    EXPECTED,

    /** The baseline has rows without such a comment, or the result differs from the baseline. */
    REMAINING,
  }

  /** The rows of a baseline file in order, and the lines that hold only a comment after the last row. */
  private class Baseline(val rows: List<BaselineRow>, val trailingLines: List<String>)

  /**
   * A row of a baseline file with the text of its trailing comment, if any (without the `#`), and the lines that hold only
   * a comment right above it.
   */
  private class BaselineRow(val row: String, val comment: String?, val commentLines: List<String> = emptyList()) {
    val isExpected: Boolean get() = comment?.startsWith(EXPECTED_MARKER, ignoreCase = true) == true
  }

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

  private fun renderSummary(total: Int, statuses: Collection<FileStatus>): String {
    val passed = statuses.count { it == FileStatus.PASSED }
    val expected = statuses.count { it == FileStatus.EXPECTED }
    // A file test that did not record a status (e.g. it threw before the comparison) counts as failed.
    val remaining = total - passed - expected
    return """
      |Typing conformance summary
      |  conformance files:                 $total
      |  passed (no baseline):              $passed
      |  passed with "expected" baseline:   $expected
      |  to be addressed / failed:          $remaining
    """.trimMargin()
  }

  private fun fileStatus(baseline: Baseline?, rows: List<String>): FileStatus = when {
    rows != baseline?.rows.orEmpty().map { it.row } -> FileStatus.REMAINING
    baseline == null -> FileStatus.PASSED
    baseline.rows.all { it.isExpected } -> FileStatus.EXPECTED
    else -> FileStatus.REMAINING
  }

  /** The baseline of [file], or `null` if [file] does not exist or has no rows. */
  private fun readBaseline(file: Path): Baseline? = if (file.exists()) parseBaseline(file.readText()) else null

  /** The rows of the baseline [text] with their comment lines, without the blank lines, or `null` if it has no rows. */
  private fun parseBaseline(text: String): Baseline? {
    val rows = mutableListOf<BaselineRow>()
    val commentLines = mutableListOf<String>()
    for (line in text.lines()) {
      val row = parseBaselineLine(line, commentLines.toList())
      when {
        row != null -> {
          rows += row
          commentLines.clear()
        }
        line.isNotBlank() -> commentLines += line.trim()
      }
    }
    return if (rows.isEmpty()) null else Baseline(rows, commentLines)
  }

  /**
   * The row and the trailing comment of a baseline line, with the [commentLines] above it, or `null` for a blank line or a
   * line that holds only a comment.
   */
  private fun parseBaselineLine(line: String, commentLines: List<String> = emptyList()): BaselineRow? {
    val match = TRAILING_COMMENT.find(line)
    val row = (if (match != null) line.substring(0, match.range.first) else line).trim()
    if (row.isEmpty()) return null
    val comment = match?.let { line.substring(it.range.first).trim().removePrefix("#").trim() }
    return BaselineRow(row, comment, commentLines)
  }

  /**
   * Compares [rows] with the rows of [baseline] read from [file]. No rows means no file.
   * With the overwrite switch, writes the rows into the file with the comments of [baseline], see [renderBaseline].
   */
  private fun compareWithFile(file: Path, baseline: Baseline?, rows: List<String>) {
    val expected = baseline?.rows?.map { it.row }
    if (rows == expected.orEmpty()) return
    if (UsefulTestCase.OVERWRITE_TESTDATA) {
      if (rows.isEmpty()) file.deleteIfExists()
      else {
        Files.createDirectories(file.parent)
        file.writeText(renderBaseline(baseline, rows).joinToString("\n", postfix = "\n"))
      }
      return
    }
    Assertions.assertEquals(
      expected?.joinToString("\n", postfix = "\n") ?: "<no baseline file>",
      rows.ifEmpty { null }?.joinToString("\n", postfix = "\n") ?: "<no rows>",
      "The conformance result differs from $file",
    )
  }

  /**
   * The lines of the baseline file for [rows]: a row that remains keeps its trailing comment and the comment lines above it.
   * The comment lines above a row that goes away move above the next row that remains, or to the end of the file.
   */
  private fun renderBaseline(baseline: Baseline?, rows: List<String>): List<String> {
    val remaining = rows.toHashSet()
    val commented = HashMap<String, String>()
    val commentLines = HashMap<String, MutableList<String>>()
    val carried = mutableListOf<String>()
    for (row in baseline?.rows.orEmpty()) {
      carried += row.commentLines
      if (row.row !in remaining) continue
      if (row.comment != null) commented[row.row] = "${row.row} # ${row.comment}"
      commentLines.getOrPut(row.row) { mutableListOf() } += carried
      carried.clear()
    }
    return buildList {
      for (row in rows) {
        commentLines.remove(row)?.let { addAll(it) }
        add(commented[row] ?: row)
      }
      addAll(carried)
      addAll(baseline?.trailingLines.orEmpty())
    }
  }

  private companion object {
    const val SUITE_DIR = "typing/conformance/tests"
    const val BASELINE_DIR = "typing/baseline"
    const val SCORES_FILE = "_scores.txt"
    val SUITE_EXTENSIONS = setOf("py", "pyi")

    /** The start of a trailing baseline comment that marks an accepted difference, compared ignoring case. */
    const val EXPECTED_MARKER = "expected"

    /** `# E`, `# E?`, `# E: message`, `# E[tag]` and `# E[tag+]` at the start of a comment. */
    val EXPECTATION = Regex("""^#\s*E(?:\[([^]\n]+)]|(\?))?(?::(.*))?(?:\s|$)""")

    /** A `#` comment at line start, or after whitespace when followed by whitespace or the end of the line. */
    val TRAILING_COMMENT = Regex("""(^|\s)#(\s|$).*""")
  }
}
