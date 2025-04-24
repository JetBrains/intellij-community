// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiComment
import com.intellij.util.io.URLUtil
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val inspections
  get() = arrayOf(
    PyAbstractClassInspection(),
    PyArgumentListInspection(),
    PyAssertTypeInspection(),
    PyCallingNonCallableInspection(),
    PyClassVarInspection(),
    PyDataclassInspection(),
    PyEnumInspection(),
    PyFinalInspection(),
    //PyInitNewSignatureInspection(), // False negative constructors_consistency.py
    PyNewStyleGenericSyntaxInspection(),
    PyNewTypeInspection(),
    PyProtocolInspection(),
    PyTypedDictInspection(),
    PyTypeCheckerInspection(),
    PyTypeHintsInspection(),
    PyUnresolvedReferencesInspection(),
  )

@RunWith(Parameterized::class)
class PyTypingConformanceTest(private val testFileName: String) : PyTestCase() {
  @Test
  fun test() {
    val settings = PyCodeInsightSettings.getInstance()
    val oldHighlightUnusedImports = settings.HIGHLIGHT_UNUSED_IMPORTS
    settings.HIGHLIGHT_UNUSED_IMPORTS = false
    try {
      myFixture.configureByFiles(*getFilePaths())
      myFixture.enableInspections(*inspections)
      checkHighlighting()
    }
    finally {
      settings.HIGHLIGHT_UNUSED_IMPORTS = oldHighlightUnusedImports
    }
  }

  private fun getFilePaths(): Array<String> {
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

  private fun checkHighlighting() {
    val document = myFixture.getDocument(myFixture.file)
    val actualErrors = myFixture.doHighlighting(HighlightSeverity.WARNING).associate {
      document.getLineNumber(it.startOffset) to it.description
    }
    val lineToError = mutableMapOf<Int, Error>()
    val errorGroups = mutableMapOf<CharSequence, ErrorGroup>()
    extractExpectedErrors(lineToError, errorGroups)
    compareErrors(lineToError, errorGroups, actualErrors)
  }

  private fun extractExpectedErrors(lineToError: MutableMap<Int, Error>, errorGroups: MutableMap<CharSequence, ErrorGroup>) {
    val document = myFixture.getDocument(myFixture.file)
    myFixture.file.accept(object : PyRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        val startOffset = comment.textRange.startOffset
        val lineNumber = document.getLineNumber(startOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineWithoutComment = document.getText(TextRange(lineStartOffset, startOffset))
        if (lineWithoutComment.isNotBlank()) {
          parseComment(comment.text, lineNumber, lineToError, errorGroups)
        }
      }
    })
  }

  private fun parseComment(
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
            val sb = StringBuilder("Inconsistent tag '$tag' usage at").appendLocation(lineNumber)
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

  private fun compareErrors(lineToError: Map<Int, Error>,
                            errorGroups: Map<CharSequence, ErrorGroup>,
                            actualErrors: Map<Int, @NlsContexts.DetailedDescription String>) {
    var missingErrorsCount = 0
    var unexpectedErrorsCount = 0
    val failMessage = StringBuilder()

    for ((lineNumber, expectedError) in lineToError) {
      if (!expectedError.isOptional) {
        val actualError = actualErrors[lineNumber]
        if (actualError == null) {
          missingErrorsCount++
          failMessage.append("Expected error at ").appendLocation(lineNumber)
          if (expectedError.message != null) {
            failMessage.append(": ").append(expectedError.message)
          }
          failMessage.appendLine()
        }
      }
    }

    for ((tag, errorGroup) in errorGroups) {
      val lines = errorGroup.lines
      val errorsCount = lines.count { it in actualErrors }
      if (errorsCount == 0 || errorsCount > 1 && !errorGroup.allowMultiple) {
        failMessage.append("Expected ")
        if (errorsCount == 0) {
          missingErrorsCount++
        }
        else {
          unexpectedErrorsCount++
          failMessage.append("single ")
        }
        failMessage.append("error (tag $tag) at ")
        failMessage.appendLocation(lines[0])
        lines.subList(1, lines.size).map { it + 1 }.joinTo(failMessage, ", ", "[", "]")
        failMessage.appendLine()
      }
    }

    val linesUsedByGroups = errorGroups.values.flatMap { it.lines }.toSet()

    for ((lineNumber, message) in actualErrors) {
      if (lineNumber !in lineToError && !linesUsedByGroups.contains(lineNumber)) {
        unexpectedErrorsCount++
        failMessage.append("Unexpected error at ").appendLocation(lineNumber).append(": ").appendLine(message)
      }
    }

    if (missingErrorsCount != 0 || unexpectedErrorsCount != 0) {
      failures.add(Failure(testFileName, missingErrorsCount, unexpectedErrorsCount))
      fail(failMessage.toString())
    }
  }

  private class Error(val message: CharSequence?, val isOptional: Boolean)

  private class ErrorGroup(val allowMultiple: Boolean) {
    val lines: MutableList<Int> = mutableListOf()
  }

  private fun StringBuilder.appendLocation(lineNumber: Int): StringBuilder {
    append(URLUtil.FILE_PROTOCOL)
    append(URLUtil.SCHEME_SEPARATOR)
    append(TESTS_DIR_ABSOLUTE_PATH / testFileName)
    append(':')
    append(lineNumber + 1)
    return this
  }

  companion object {
    private const val TESTS_DIR = "typing/conformance/tests"
    private val TESTS_DIR_ABSOLUTE_PATH = Path.of(PythonTestUtil.getTestDataPath(), TESTS_DIR)
    private val IGNORED_TESTS = Files.readAllLines(TESTS_DIR_ABSOLUTE_PATH / "_ignored.txt").toSet()
    private val failures = mutableListOf<Failure>()

    private class Failure(val testFileName: String, val missingErrorsCount: Int, val unexpectedErrorsCount: Int)

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters(): List<String> {
      return TESTS_DIR_ABSOLUTE_PATH.listDirectoryEntries()
        .map(Path::name)
        .filter { !it.startsWith('_') }
        .filter { it !in IGNORED_TESTS }
    }

    @AfterClass
    @JvmStatic
    fun afterClass() {
      if (failures.isNotEmpty()) {
        val missingErrorsCount = failures.sumOf { it.missingErrorsCount }
        val unexpectedErrorsCount = failures.sumOf { it.unexpectedErrorsCount }
        println("Test failed: missing errors: $missingErrorsCount; unexpected errors: $unexpectedErrorsCount")

        failures.sortedBy { it.missingErrorsCount + it.unexpectedErrorsCount }.forEach {
          println("${it.testFileName} ${it.missingErrorsCount} ${it.unexpectedErrorsCount}")
        }
      }
    }
  }
}