package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.report.ErrorIgnorer
import com.intellij.ide.starter.report.ErrorReporter
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.kodein.di.DI
import org.kodein.di.bindSingleton

/**
 * Disables error reporting, needed for tests in the community folder, as diogen is not available there, AT-4065
 */
class SetErrorIgnorerExtension : BeforeAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    di = DI.Companion {
      extend(di)
      bindSingleton<ErrorReporter>(overrides = true) { ErrorIgnorer }
    }
  }
}