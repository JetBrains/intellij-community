package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.report.ErrorReporter.Companion.MESSAGE_FILENAME
import com.intellij.ide.starter.report.ErrorReporter.Companion.STACKTRACE_FILENAME
import com.intellij.ide.starter.report.ErrorReporter.Companion.TESTNAME_FILENAME
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.util.SystemProperties
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.jvm.optionals.getOrNull

object ErrorReporterToCI: ErrorReporter {
  /**
   * Read files from errors directories, written by performance testing plugin and report them as errors.
   * Read threadDumps folders and report them as freezes.
   * Take a look at [com.jetbrains.performancePlugin.ProjectLoaded.reportErrorsFromMessagePool]
   */
  override fun reportErrorsAsFailedTests(runContext: IDERunContext) {
    reportErrors(runContext, collectErrors(runContext.logsDir) + collectScriptErrors(runContext.logsDir))
  }

  fun collectErrors(logsDir: Path): List<Error> {
    //client has structure log/2024-04-11_at_11-06-10/script-errors so we need to look deeeper
    val rootErrorsDir = Files.find(logsDir, 3, { path, _ -> path.name == ErrorReporter.ERRORS_DIR_NAME }).findFirst().getOrNull()
    if (SystemProperties.getBooleanProperty("DO_NOT_REPORT_ERRORS", false)) return listOf()
    return collectExceptions(rootErrorsDir)
  }

  /**
   * To support legacy formant of errors reporting in "script-errors" dir
   */
  fun collectScriptErrors(logsDir: Path): List<Error> {
    val rootErrorsDir = Files.find(logsDir, 3, { path, _ -> path.name == "script-" + ErrorReporter.ERRORS_DIR_NAME }).findFirst().getOrNull()
    if (SystemProperties.getBooleanProperty("DO_NOT_REPORT_ERRORS", false)) return listOf()
    return collectExceptions(rootErrorsDir)
  }

  /**
   * Method only collects exceptions from [ErrorReporter.ERRORS_DIR_NAME] and skip freezes
   */
  private fun collectExceptions(rootErrorsDir: Path?): List<Error> {
    if (rootErrorsDir == null || !rootErrorsDir.isDirectory()) {
      return emptyList()
    }
    val errors = mutableListOf<Error>()
    val errorsDirectories = rootErrorsDir.listDirectoryEntries()
    for (errorDir in errorsDirectories) {
      val messageFile = errorDir.resolve(MESSAGE_FILENAME).toFile()
      if (!messageFile.exists()) continue

      val messageText = generifyErrorMessage(messageFile.readText().trimIndent().trim())
      val testNameFile = errorDir.resolve(TESTNAME_FILENAME).toFile()
      val testName = if (testNameFile.exists()) testNameFile.readText().trim() else null

      val errorType = ErrorType.fromMessage(messageText)
      if (errorType == ErrorType.ERROR) {
        val stacktraceFile = errorDir.resolve(STACKTRACE_FILENAME).toFile()
        if (!stacktraceFile.exists()) continue
        val stackTrace = stacktraceFile.readText().trimIndent().trim()
        errors.add(Error(messageText, stackTrace, "", errorType, testName))
      } else if (errorType == ErrorType.FREEZE) {
        errorDir.listDirectoryEntries("dump*").firstOrNull()?.let { threadDump ->
          val dumpContent = Files.readString(threadDump)
          val fallbackName = "Not analyzed freeze: " + (inferClassMethodNamesFromFolderName(threadDump)
                                                        ?: inferFallbackNameFromThreadDump(dumpContent))
          errors.add(Error(fallbackName, "", dumpContent, ErrorType.FREEZE))
        }
      }
    }
    return errors
  }

  /**
   * There are two types of names for folders with freezes:
   * ```
   *  threadDumps-freeze-20240206-155640-IU-241.11817
   *  threadDumps-freeze-20240206-155640-IU-241.11817-JBIterator.peekNext-5sec
   *  ```
   *
   *  Return `null` if folder has the first type.
   *
   *  Infer the class and method name from the second type taking the part before the latest - `nameparts[7]`.
   */
  private fun inferClassMethodNamesFromFolderName(path: Path): String? {
    val nameParts = path.name.split("-")
    return if (nameParts.size == 8) nameParts[7] else null
  }

  /**
   * Takes the first line that looks like at com.intellij.util.containers.JBIterator.peekNext(JBIterator.java:132)
   * @return className.methodName (e.g., JBIterator.peekNext)
   */
  private fun inferFallbackNameFromThreadDump(dumpContent: String): String {
    val regex = Regex("at (.*)\\(.*:\\d+\\)")
    dumpContent.lineSequence()
      .mapNotNull { line ->
        regex.find(line.trim())?.let { match ->
          match.groupValues[1].split(".").takeLast(2).joinToString(".")
        }
      }
      .firstOrNull()?.let { return it }

    throw Exception("Thread dump file without methods!")
  }

  fun reportErrors(runContext: IDERunContext, errors: List<Error>) {
    for (error in errors) {
      val messageText = error.messageText
      val stackTraceContent = error.stackTraceContent
      val testName = when (error.type) {
        ErrorType.ERROR -> {
          error.testName ?: generateTestNameFromException(stackTraceContent, messageText)
        }
        ErrorType.FREEZE, ErrorType.TIMEOUT -> {
          messageText
        }
      }

      val failureDetailsProvider = FailureDetailsOnCI.instance
      val failureDetailsMessage = failureDetailsProvider.getFailureDetails(runContext)
      val urlToLogs = failureDetailsProvider.getLinkToCIArtifacts(runContext).toString()
      if (CIServer.instance.isTestFailureShouldBeIgnored(messageText) || CIServer.instance.isTestFailureShouldBeIgnored(stackTraceContent)) {
        CIServer.instance.ignoreTestFailure(testName = "(${generifyErrorMessage(testName)})",
                                            message = failureDetailsMessage)
      }
      else {
        CIServer.instance.reportTestFailure(testName = "(${generifyErrorMessage(testName)})",
                                            message = failureDetailsMessage,
                                            details = stackTraceContent,
                                            linkToLogs = urlToLogs)
        AllureReport.reportFailure(runContext.contextName, messageText,
                                   stackTraceContent,
                                   links = AllureLink.single("Link to Logs and artifacts", failureDetailsProvider.getLinkToCIArtifacts(runContext) ?: "fail to get link"))
      }
    }
  }

  private fun generateTestNameFromException(stackTraceContent: String, messageText: String): String {
    return if (stackTraceContent.startsWith(messageText)) {
      val maxLength = (ErrorReporter.MAX_TEST_NAME_LENGTH).coerceAtMost(stackTraceContent.length)
      val extractedTestName = stackTraceContent.substring(0, maxLength).trim()
      extractedTestName
    }
    else {
      messageText.substring(0, ErrorReporter.MAX_TEST_NAME_LENGTH.coerceAtMost(messageText.length)).trim()
    }
  }
}
