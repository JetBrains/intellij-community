// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.Executor
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.ui.ConsoleView

internal class SurefireTestConsoleProperties(
  private val mavenConfiguration: MavenSurefireRunConfiguration,
  executor: Executor,
) : SMTRunnerConsoleProperties(mavenConfiguration, "Maven Surefire", executor) {

  override fun createRerunFailedTestsAction(consoleView: ConsoleView): AbstractRerunFailedTestsAction =
    SurefireRerunFailedTestsAction(consoleView, mavenConfiguration).also { it.init(this) }
}
