// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.cleanApplicationState
import com.intellij.testFramework.recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit5 extension that calls [cleanApplicationState] after each test.
 *
 * This extension runs after all `addPostCleanup` callbacks have completed,
 * ensuring that fixtures are properly torn down before application state cleanup.
 */
class CleanApplicationStateExtension : AfterEachCallback {
  override fun afterEach(context: ExtensionContext) {
    recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures {
      ApplicationManager.getApplication()?.cleanApplicationState()
    }
  }
}
