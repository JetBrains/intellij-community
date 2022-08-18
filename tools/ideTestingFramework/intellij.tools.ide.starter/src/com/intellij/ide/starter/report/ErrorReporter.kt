package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

object ErrorReporter {
  private const val MAX_TEST_NAME_LENGTH = 250

  private fun getTestMethodName(): String {
    val method = di.direct.instance<CurrentTestMethod>().get()
    return if (method == null) "" else "${method.declaringClass.name}.${method.name}"
  }

  /**
   * Sort things out from errors directories, written by performance testing plugin
   * Take a look at [com.jetbrains.performancePlugin.ProjectLoaded.reportErrorsFromMessagePool]
   */
  fun reportErrorsAsFailedTests(scriptErrorsDir: Path, contextName: String): List<Pair<File, File>> {
    val testMethodName = getTestMethodName().ifEmpty { contextName }

    return if (scriptErrorsDir.isDirectory()) {
      val errorsDirectories = scriptErrorsDir.listDirectoryEntries()

      errorsDirectories.map { errorDir ->
        val messageFile = errorDir.resolve("message.txt").toFile()
        val stacktraceFile = errorDir.resolve("stacktrace.txt").toFile()

        if (messageFile.exists() && stacktraceFile.exists()) {
          val messageText = generifyErrorMessage(messageFile.readText().trimIndent().trim())
          val stackTraceContent = stacktraceFile.readText().trimIndent().trim()

          val testName: String

          val onlyLettersHash = convertToHashCodeWithOnlyLetters(generifyErrorMessage(stackTraceContent).hashCode())

          if (stackTraceContent.startsWith(messageText)) {
            val maxLength = (MAX_TEST_NAME_LENGTH - onlyLettersHash.length).coerceAtMost(stackTraceContent.length)
            val extractedTestName = stackTraceContent.substring(0, maxLength).trim()
            testName = "($onlyLettersHash $extractedTestName)"
          }
          else {
            testName = "($onlyLettersHash ${messageText.substring(0, MAX_TEST_NAME_LENGTH.coerceAtMost(messageText.length)).trim()})"
          }

          val failureDetails = "Test: $testMethodName" + System.lineSeparator() +
                               "You can find an idea.log and other useful info in TC artifacts under the path $contextName" + System.lineSeparator() +
                               stackTraceContent

          di.direct.instance<CIServer>().reportTestFailure(testName = generifyErrorMessage(testName),
                                                           message = messageText,
                                                           details = failureDetails)
        }

        Pair(messageFile, stacktraceFile)
      }
    }
    else listOf()
  }
}
