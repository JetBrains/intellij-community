// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiComment
import com.intellij.util.io.URLUtil
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.fixtures.PyTestCase
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

private val IGNORED_INSPECTIONS = listOf(
  PyPep8Inspection::class.java,
  PyInterpreterInspection::class.java,
  PyRedeclarationInspection::class.java,
  PyStatementEffectInspection::class.java,
).map { it.name }

@Ignore
@RunWith(Parameterized::class)
class PyTypingConformanceTest(private val testFileName: String) : PyTestCase() {
  @Test
  fun test() {
    val files = myFixture.configureByFiles(*getFilePaths().toTypedArray())
    enableInspections()
    checkHighlighting()
    //assertNotParsed(files[0])
  }

  private fun getFilePaths(): List<String> {
    return Stream
      .concat(Stream.of(testFileName), listTestFileDependencies(testFileName))
      .map { Path.of(TESTS_DIR, it).toString() }
      .toList()
  }

  private fun enableInspections() {
    val inspections = sequenceOf(LocalInspectionEP.LOCAL_INSPECTION, LocalInspectionEP.GLOBAL_INSPECTION)
      .flatMap { it.extensionList }
      .filter { !IGNORED_INSPECTIONS.contains(it.implementationClass) && it.language == PythonLanguage.INSTANCE.id && it.enabledByDefault }
      .map(InspectionEP::instantiateTool)
      .toList()
      .toTypedArray()

    myFixture.enableInspections(*inspections)
  }

  private fun checkHighlighting() {
    val document = myFixture.getDocument(myFixture.file)
    val errors = myFixture.doHighlighting(HighlightSeverity.WARNING).associate {
      document.getLineNumber(it.startOffset) to it.description
    }
    compareErrors(document, extractExpectedErrors(), errors)
  }

  private fun extractExpectedErrors(): Map<Int, Error> {
    val result = mutableMapOf<Int, Error>()
    val document = myFixture.getDocument(myFixture.file)
    myFixture.file.accept(object : PyRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        super.visitComment(comment)
        val error = tryParseError(comment.text)
        if (error != null) {
          result[document.getLineNumber(comment.textRange.startOffset)] = error
        }
      }
    })
    return result
  }

  private fun tryParseError(comment: CharSequence): Error? {
    assert(comment.startsWith("#"))
    var index = 1
    while (index < comment.length && comment[index].isWhitespace()) {
      index++
    }
    if (index < comment.length && comment[index] == 'E') {
      index++
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
      return Error(message, optional)
    }
    return null
  }

  private class Error(val message: CharSequence?, val isOptional: Boolean)

  private fun compareErrors(
    document: Document,
    expectedErrors: Map<Int, Error>,
    actualErrors: Map<Int, @NlsContexts.DetailedDescription String>,
  ) {
    var failure = false
    val failMessage = StringBuilder()
    for (lineNumber in 0..<document.lineCount) {
      val expectedError = expectedErrors[lineNumber]
      if (expectedError != null) {
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
      else {
        val actualError = actualErrors[lineNumber]
        if (actualError != null) {
          failure = true
          failMessage.append("Unexpected error at ").appendLocation(lineNumber).append(": ").appendLine(actualError)
        }
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