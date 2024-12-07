// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiComment
import com.intellij.util.io.URLUtil
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.div
import kotlin.io.path.name

private const val CHECK_OPTIONAL_ERRORS = false
private const val TESTS_DIR = "typing/conformance/tests"
private const val RUN_ALL_INSPECTIONS = false

private val inspections
  get() = arrayOf(
    PyArgumentListInspection(),
    PyAssertTypeInspection(),
    PyDataclassInspection(),
    PyEnumInspection(),
    //PyInitNewSignatureInspection(), // False negative constructors_consistency.py
    PyNewStyleGenericSyntaxInspection(),
    PyNewTypeInspection(),
    PyTypedDictInspection(),
    PyTypeCheckerInspection(),
    PyTypeHintsInspection(),
    PyUnresolvedReferencesInspection(),
  )

private val IGNORED_INSPECTIONS = listOf(
  PyInitNewSignatureInspection::class, // False negative constructors_consistency.py
  PyInterpreterInspection::class,
  PyPep8Inspection::class,
  PyRedeclarationInspection::class,
  PyStatementEffectInspection::class,
).map { it.java.name }

@Ignore
@RunWith(Parameterized::class)
class PyTypingConformanceTest(private val testFileName: String) : PyTestCase() {
  @Test
  fun test() {
    val settings = PyCodeInsightSettings.getInstance()
    val oldHighlightUnusedImports = settings.HIGHLIGHT_UNUSED_IMPORTS
    settings.HIGHLIGHT_UNUSED_IMPORTS = false
    try {
      myFixture.configureByFiles(*getFilePaths().toTypedArray())
      myFixture.enableInspections(*getInspections())
      checkHighlighting()
    }
    finally {
      settings.HIGHLIGHT_UNUSED_IMPORTS = oldHighlightUnusedImports
    }
  }

  private fun getFilePaths(): List<String> {
    return Stream
      .concat(Stream.of(testFileName), listTestFileDependencies(testFileName))
      .map { Path.of(TESTS_DIR, it).toString() }
      .toList()
  }

  private fun getInspections(): Array<out InspectionProfileEntry> = if (RUN_ALL_INSPECTIONS)
    sequenceOf(LocalInspectionEP.LOCAL_INSPECTION, LocalInspectionEP.GLOBAL_INSPECTION)
      .flatMap { it.extensionList }
      .filter { !IGNORED_INSPECTIONS.contains(it.implementationClass) && it.language == PythonLanguage.INSTANCE.id && it.enabledByDefault }
      .map(InspectionEP::instantiateTool)
      .toList()
      .toTypedArray<InspectionProfileEntry>()
  else
    inspections

  private fun checkHighlighting() {
    val document = myFixture.getDocument(myFixture.file)
    val errors = myFixture.doHighlighting(HighlightSeverity.WARNING).associate {
      document.getLineNumber(it.startOffset) to it.description
    }
    compareErrors(extractExpectedErrors(), errors)
  }

  private fun extractExpectedErrors(): Errors {
    val lineToError = mutableMapOf<Int, Error>()
    val errorGroups = mutableMapOf<CharSequence, MutableList<Int>>()
    val document = myFixture.getDocument(myFixture.file)
    myFixture.file.accept(object : PyRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        val startOffset = comment.textRange.startOffset
        val lineNumber = document.getLineNumber(startOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineWithoutComment = document.getText(TextRange(lineStartOffset, startOffset))
        if (lineWithoutComment.isNotBlank()) {
          parseComment(comment.text,
                       { lineToError[lineNumber] = it },
                       { errorGroups.computeIfAbsent(it) { mutableListOf() }.add(lineNumber) })
        }
      }
    })
    return Errors(lineToError, errorGroups)
  }

  private fun parseComment(comment: CharSequence, onError: (Error) -> Unit, onErrorTag: (CharSequence) -> Unit) {
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
          onErrorTag(comment.subSequence(index + 1, endIndex))
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
        onError(Error(message, optional))
      }
    }
  }

  private data class Error(val message: CharSequence?, val isOptional: Boolean)

  private data class Errors(val lineToError: Map<Int, Error>, val errorGroups: Map<CharSequence, List<Int>>)

  private fun compareErrors(expectedErrors: Errors, actualErrors: Map<Int, @NlsContexts.DetailedDescription String>) {
    var failure = false
    val failMessage = StringBuilder()

    for ((lineNumber, expectedError) in expectedErrors.lineToError) {
      if (CHECK_OPTIONAL_ERRORS || !expectedError.isOptional) {
        val actualError = actualErrors[lineNumber]
        if (actualError == null) {
          failure = true
          failMessage.append("Missing error at ").appendLocation(lineNumber)
          if (expectedError.message != null) {
            failMessage.append(": ").append(expectedError.message)
          }
          failMessage.appendLine()
        }
      }
    }

    for ((tag, lineNumbers) in expectedErrors.errorGroups) {
      if (!lineNumbers.isEmpty() && lineNumbers.all { it !in actualErrors }) {
        failure = true
        failMessage.append("Missing error (tag $tag) at ").appendLocation(lineNumbers[0])
        lineNumbers.subList(1, lineNumbers.size).map { it + 1 }.joinTo(failMessage, ", ", "[", "]")
        failMessage.appendLine()
      }
    }

    val linesUsedByGroups = expectedErrors.errorGroups.values.flatten().toSet()

    for ((lineNumber, message) in actualErrors) {
      if (lineNumber !in expectedErrors.lineToError && !linesUsedByGroups.contains(lineNumber)) {
        failure = true
        failMessage.append("Unexpected error at ").appendLocation(lineNumber).append(": ").appendLine(message)
      }
    }

    if (failure) {
      fail(failMessage.toString())
    }
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
    private val TESTS_DIR_ABSOLUTE_PATH = Path.of(PythonTestUtil.getTestDataPath(), TESTS_DIR)

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters(): List<String> {
      return listAllFiles()
        .filter { !it.startsWith("_") }
        .toList()
    }

    private fun listTestFileDependencies(testFileName: String): Stream<String> {
      val dependenciesPrefix = "_${FileUtil.getNameWithoutExtension(testFileName)}"
      return listAllFiles().filter { it.startsWith(dependenciesPrefix) }
    }

    private fun listAllFiles(): Stream<String> {
      return Files.list(TESTS_DIR_ABSOLUTE_PATH).map { it.name }
    }
  }
}