package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

object ErrorReporter {
  private const val MAX_TEST_NAME_LENGTH = 250

  /**
   * Read files from errors directories, written by performance testing plugin.
   * Report them as an individual failures on CI
   * Take a look at [com.jetbrains.performancePlugin.ProjectLoaded.reportErrorsFromMessagePool]
   */
  fun reportErrorsAsFailedTests(rootErrorsDir: Path, runContext: IDERunContext) {
    if (!rootErrorsDir.isDirectory()) return

    val errorsDirectories = rootErrorsDir.listDirectoryEntries()

    errorsDirectories.forEach { errorDir ->
      val messageFile = errorDir.resolve("message.txt").toFile()
      val stacktraceFile = errorDir.resolve("stacktrace.txt").toFile()

      if (messageFile.exists() && stacktraceFile.exists()) {
        val messageText = generifyErrorMessage(messageFile.readText().trimIndent().trim())
        val stackTraceContent = stacktraceFile.readText().trimIndent().trim()

         val errorShouldBeIgnored = di.direct.instance<CIServer>().checkIfShouldBeIgnored(messageText)

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

        val failureDetails = di.direct.instance<FailureDetailsOnCI>().getFailureDetails(runContext)

        if (errorShouldBeIgnored) {
          di.direct.instance<CIServer>().ignoreTestFailure(testName = generifyErrorMessage(testName),
                                                           message = failureDetails,
                                                           details = stackTraceContent)
        }
        else {
          di.direct.instance<CIServer>().reportTestFailure(testName = generifyErrorMessage(testName),
                                                           message = failureDetails,
                                                           details = stackTraceContent)
        }
      }
    }
  }
}
