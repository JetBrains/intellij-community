package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
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

  /**
   * Sort things out from errors directories, written by performance testing plugin
   * Take a look at [com.jetbrains.performancePlugin.ProjectLoaded.reportErrorsFromMessagePool]
   */
  fun reportErrorsAsFailedTests(scriptErrorsDir: Path, contextName: String): List<Pair<File, File>> {
    return if (scriptErrorsDir.isDirectory()) {
      val errorsDirectories = scriptErrorsDir.listDirectoryEntries()

      errorsDirectories.map { errorDir ->
        val messageFile = errorDir.resolve("message.txt").toFile()
        val stacktraceFile = errorDir.resolve("stacktrace.txt").toFile()

        if (messageFile.exists() && stacktraceFile.exists()) {
          val messageText = generifyErrorMessage(messageFile.readText().trim())
          val stackTraceContent = stacktraceFile.readText().trim()

          var testName: String

          val onlyLettersHash = convertToHashCodeWithOnlyLetters(generifyErrorMessage(stackTraceContent).hashCode())

          if (stackTraceContent.startsWith(messageText)) {
            val maxLength = (MAX_TEST_NAME_LENGTH - onlyLettersHash.length).coerceAtMost(stackTraceContent.length)
            val extractedTestName = stackTraceContent.substring(0, maxLength).trim()
            testName = "($onlyLettersHash $extractedTestName)"
          }
          else {
            testName = "($onlyLettersHash ${messageText.substring(0, MAX_TEST_NAME_LENGTH.coerceAtMost(messageText.length)).trim()})"
          }

          val stackTrace = """
                Test: $contextName
                
                $stackTraceContent
              """.trimIndent().trimMargin().trim()

          di.direct.instance<CIServer>().reportTestFailure(generifyErrorMessage(testName), messageText, stackTrace)
        }

        Pair(messageFile, stacktraceFile)
      }
    }
    else listOf()
  }
}
