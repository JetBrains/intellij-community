// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools.sdkTools

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiComment
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.io.URLUtil
import com.jetbrains.python.inspections.PyAbstractClassInspection
import com.jetbrains.python.inspections.PyArgumentListInspection
import com.jetbrains.python.inspections.PyAssertTypeInspection
import com.jetbrains.python.inspections.PyCallingNonCallableInspection
import com.jetbrains.python.inspections.PyClassVarInspection
import com.jetbrains.python.inspections.PyDataclassInspection
import com.jetbrains.python.inspections.PyDunderSlotsInspection
import com.jetbrains.python.inspections.PyEnumInspection
import com.jetbrains.python.inspections.PyFinalInspection
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyNewStyleGenericSyntaxInspection
import com.jetbrains.python.inspections.PyNewTypeInspection
import com.jetbrains.python.inspections.PyOverloadsInspection
import com.jetbrains.python.inspections.PyOverridesInspection
import com.jetbrains.python.inspections.PyProtocolInspection
import com.jetbrains.python.inspections.PyTypeAliasRedeclarationInspection
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.PyTypeHintsInspection
import com.jetbrains.python.inspections.PyTypedDictInspection
import com.jetbrains.python.inspections.PyVarianceInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import org.junit.jupiter.api.Assertions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

object PyConformanceTestSuiteUtils {
  const val TESTS_DIR: String = "typing/conformance/tests"

  val TEST_DATA_PATH: String by lazy {
    val jarPath: String? = PathManager.getJarPathForClass(PyConformanceTestSuiteUtils::class.java)
    requireNotNull(jarPath) { "Cannot find jar path for test class" }

    var current: Path? = Path.of(jarPath)
    while (current != null) {
      val communityPath = current.resolve("community/python/testData")
      if (Files.isDirectory(communityPath)) {
        return@lazy communityPath.toString()
      }
      current = current.parent
    }

    PythonHelpersLocator.getPythonCommunityPath().resolve("testData").toString()
  }

  val TESTS_DIR_ABSOLUTE_PATH: Path get() = Path.of(TEST_DATA_PATH, TESTS_DIR)

  val failures: MutableList<Failure> = mutableListOf<Failure>()

  val pythonConformanceSuiteInspections: Array<PyInspection>
    get() = arrayOf(
      PyAbstractClassInspection(),
      PyArgumentListInspection(),
      PyAssertTypeInspection(),
      PyCallingNonCallableInspection(),
      PyClassVarInspection(),
      PyDataclassInspection(),
      PyDunderSlotsInspection(),
      PyEnumInspection(),
      PyFinalInspection(),
      //PyInitNewSignatureInspection(), // False negative constructors_consistency.py
      PyNewStyleGenericSyntaxInspection(),
      PyNewTypeInspection(),
      PyOverloadsInspection(),
      PyOverridesInspection(),
      PyProtocolInspection(),
      PyTypedDictInspection(),
      PyTypeCheckerInspection(),
      PyTypeHintsInspection(),
      PyVarianceInspection(),
      PyUnresolvedReferencesInspection(),
      PyTypeAliasRedeclarationInspection(),
    )

  fun getFilePaths(testFileName: String): Array<String> {
    val dependenciesPrefix = "_${FileUtil.getNameWithoutExtension(testFileName)}"
    val dependencies = TESTS_DIR_ABSOLUTE_PATH.listDirectoryEntries()
      .asSequence()
      .map(Path::name)
      .filter { it.startsWith(dependenciesPrefix) }

    return sequenceOf(testFileName)
      .plus(dependencies)
      .map { Path.of(TESTS_DIR, it).toString() }
      .toList()
      .toTypedArray()
  }

  fun getTestFiles(): List<String> {
    val ignoredTests = Files.readAllLines(Path.of(TEST_DATA_PATH, "typing/ignored.txt")).toSet()
    return TESTS_DIR_ABSOLUTE_PATH.listDirectoryEntries()
      .map(Path::name)
      .filter { !it.startsWith('_') && it !in ignoredTests }
  }

  @JvmStatic
  fun checkHighlighting(fixture: CodeInsightTestFixture, testFileName: String) {
    val document = fixture.getDocument(fixture.file)
    val actualErrors = fixture.doHighlighting(HighlightSeverity.WARNING).associate {
      document.getLineNumber(it.startOffset) to it.description
    }
    val lineToError = mutableMapOf<Int, Error>()
    val errorGroups = mutableMapOf<CharSequence, ErrorGroup>()
    extractExpectedErrors(fixture, testFileName, lineToError, errorGroups)
    compareErrors(testFileName, lineToError, errorGroups, actualErrors)
  }

  private fun extractExpectedErrors(fixture: CodeInsightTestFixture, testFileName: String, lineToError: MutableMap<Int, Error>, errorGroups: MutableMap<CharSequence, ErrorGroup>) {
    val document = fixture.getDocument(fixture.file)
    fixture.file.accept(object : PyRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        val startOffset = comment.textRange.startOffset
        val lineNumber = document.getLineNumber(startOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineWithoutComment = document.getText(TextRange(lineStartOffset, startOffset))
        if (lineWithoutComment.isNotBlank()) {
          parseComment(testFileName, comment.text, lineNumber, lineToError, errorGroups)
        }
      }
    })
  }

  private fun parseComment(
    testFileName: String,
    comment: @NlsSafe String,
    lineNumber: Int,
    lineToError: MutableMap<Int, Error>,
    errorGroups: MutableMap<CharSequence, ErrorGroup>,
  ) {
    assert(comment.startsWith("#"))
    var index = 1
    while (index < comment.length && comment[index].isWhitespace()) {
      index++
    }
    if (index < comment.length && comment[index] == 'E') {
      index++

      if (index < comment.length && comment[index] == '[') {
        val endIndex = comment.indexOfAny(charArrayOf(']', '\n'), index + 1, false)
        if (endIndex != -1 && comment[endIndex] != '\n') {
          val tag: CharSequence
          val allowMultiple: Boolean
          if (comment[endIndex - 1] == '+') {
            tag = comment.subSequence(index + 1, endIndex - 1)
            allowMultiple = true
          }
          else {
            tag = comment.subSequence(index + 1, endIndex)
            allowMultiple = false
          }
          val errorGroup = errorGroups.computeIfAbsent(tag) { ErrorGroup(allowMultiple) }
          if (errorGroup.allowMultiple != allowMultiple) {
            val sb = StringBuilder("Inconsistent tag '$tag' usage at").appendLocation(testFileName, lineNumber)
            throw IllegalArgumentException(sb.toString())
          }
          errorGroup.lines.add(lineNumber)
        }
      }
      else {
        val optional: Boolean
        if (index < comment.length && comment[index] == '?') {
          optional = true
          index++
        }
        else {
          optional = false
        }
        val message: CharSequence?
        if (index < comment.length && comment[index] == ':') {
          index++
          message = comment.subSequence(index, comment.length).trimStart()
        }
        else {
          message = null
        }
        lineToError[lineNumber] = Error(message, optional)
      }
    }
  }

  private fun compareErrors(
    testFileName: String,
    lineToError: Map<Int, Error>,
    errorGroups: Map<CharSequence, ErrorGroup>,
    actualErrors: Map<Int, @NlsContexts.DetailedDescription String>,
  ) {
    val missingErrors = mutableListOf<Pair<Int, CharSequence>>()
    val unexpectedErrors = mutableListOf<Pair<Int, CharSequence>>()

    for ((lineNumber, expectedError) in lineToError) {
      if (!expectedError.isOptional) {
        val actualError = actualErrors[lineNumber]
        if (actualError == null) {
          val message = StringBuilder("Expected error at ").appendLocation(testFileName, lineNumber)
          if (expectedError.message != null) {
            message.append(": ").append(expectedError.message)
          }
          missingErrors.add(lineNumber to message)
        }
      }
    }

    for ((tag, errorGroup) in errorGroups) {
      val lines = errorGroup.lines
      val errorsCount = lines.count { it in actualErrors }
      if (errorsCount == 0 || errorsCount > 1 && !errorGroup.allowMultiple) {
        val message = StringBuilder("Expected ")
        if (errorsCount != 0) {
          message.append("single ")
        }
        message.append("error (tag $tag) at ")
        message.appendLocation(testFileName, lines[0])
        lines.subList(1, lines.size).map { it + 1 }.joinTo(message, ", ", "[", "]")
        val errors = if (errorsCount == 0) missingErrors else unexpectedErrors
        errors.add(lines[0] to message)
      }
    }

    val linesUsedByGroups = errorGroups.values.flatMap { it.lines }.toSet()

    for ((lineNumber, message) in actualErrors) {
      if (lineNumber !in lineToError && !linesUsedByGroups.contains(lineNumber)) {
        unexpectedErrors.add(
          lineNumber to StringBuilder("Unexpected error at ").appendLocation(testFileName, lineNumber).append(": ").append(message)
        )
      }
    }

    if (missingErrors.isNotEmpty() || unexpectedErrors.isNotEmpty()) {
      failures.add(Failure(testFileName, missingErrors.size, unexpectedErrors.size))
      val message: String = (missingErrors.asSequence() + unexpectedErrors.asSequence())
        .sortedBy(Pair<Int, CharSequence>::first)
        .joinToString(separator = System.lineSeparator(), transform = Pair<Int, CharSequence>::second)

      Assertions.fail<String>(message)
    }
  }

  private fun StringBuilder.appendLocation(testFileName: String, lineNumber: Int): StringBuilder {
    append(URLUtil.FILE_PROTOCOL)
    append(URLUtil.SCHEME_SEPARATOR)
    append(TESTS_DIR_ABSOLUTE_PATH / testFileName)
    append(':')
    append(lineNumber + 1)
    return this
  }

  private class Error(val message: CharSequence?, val isOptional: Boolean)

  private class ErrorGroup(val allowMultiple: Boolean) {
    val lines: MutableList<Int> = mutableListOf()
  }

  class Failure(val testFileName: String, val missingErrorsCount: Int, val unexpectedErrorsCount: Int)
}