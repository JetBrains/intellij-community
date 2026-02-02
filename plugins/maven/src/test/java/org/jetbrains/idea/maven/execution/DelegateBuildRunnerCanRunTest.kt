// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.maven.testFramework.MavenExecutionTestCase
import org.jetbrains.idea.maven.execution.build.DELEGATE_RUNNER_ID
import org.junit.Test

class DelegateBuildRunnerCanRunTest : MavenExecutionTestCase() {

  @Test
  fun testAcceptsMavenRunConfiguration() {
    val runner = ProgramRunner.findRunnerById(DELEGATE_RUNNER_ID)?: throw AssertionError("[$DELEGATE_RUNNER_ID] build runner should be registered")
    val mavenFactory = MavenRunConfigurationType.getInstance().configurationFactories[0]
    val mavenConfiguration = mavenFactory.createTemplateConfiguration(project)

    assertTrue(
      "Delegate build runner must accept Maven run configuration with DefaultRunExecutor",
      runner.canRun(DefaultRunExecutor.EXECUTOR_ID, mavenConfiguration)
    )
  }

  @Test
  fun testDoesNotAcceptGenericApplicationRunConfiguration() {
    val runner = ProgramRunner.findRunnerById(DELEGATE_RUNNER_ID) ?: throw AssertionError("[$DELEGATE_RUNNER_ID] build runner should be registered")
    val appFactory = ApplicationConfigurationType.getInstance().configurationFactories[0]
    val appConfiguration = appFactory.createTemplateConfiguration(project)

    assertFalse(
      "Delegate build runner must not accept generic Application run configuration",
      runner.canRun(DefaultRunExecutor.EXECUTOR_ID, appConfiguration)
    )
  }
}
