package com.intellij.ide.starter.report

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.IDERunContext
import org.kodein.di.direct
import org.kodein.di.instance

interface ErrorReporter {
  fun reportErrorsAsFailedTests(runContext: IDERunContext)
  companion object {
    const val MESSAGE_FILENAME = "message.txt"
    const val TESTNAME_FILENAME = "testName.txt"
    const val STACKTRACE_FILENAME = "stacktrace.txt"
    const val PRODUCT_INFO_FILENAME = "product_info.txt"
    const val ERRORS_DIR_NAME = "errors"
    const val MAX_TEST_NAME_LENGTH = 250
    const val APP_NAME_KEY = "app.name"
    const val APP_NAME_FULL_KEY = "app.name.full"
    val instance: ErrorReporter
      get() = di.direct.instance<ErrorReporter>()
  }
}