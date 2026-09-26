package com.intellij.ide.starter.report

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.IDERunContext
import org.kodein.di.direct
import org.kodein.di.instance

interface ErrorReporter {
  fun reportErrorsAsFailedTests(runContext: IDERunContext)
  companion object {
    const val MESSAGE_FILENAME = "message.txt"
    const val SYNTHETIC_TESTNAME_FILENAME = "syntheticTestName.txt"
    const val STACKTRACE_FILENAME = "stacktrace.txt"
    const val PRODUCT_INFO_FILENAME = "product_info.txt"
    const val ERRORS_DIR_NAME = "errors"
    const val APP_NAME_KEY = "app.name"
    const val APP_PRODUCT_CODE_KEY = "app.product.code"
    const val APP_BUILD_NUMBER_KEY = "app.build.number"
    const val APP_NAME_FULL_KEY = "app.name.full"
    val instance: ErrorReporter
      get() = di.direct.instance<ErrorReporter>()
  }
}