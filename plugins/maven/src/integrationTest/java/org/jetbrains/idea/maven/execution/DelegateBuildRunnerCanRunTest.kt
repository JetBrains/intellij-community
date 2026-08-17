// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import org.jetbrains.idea.maven.execution.build.DELEGATE_RUNNER_ID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class DelegateBuildRunnerCanRunTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  private lateinit var jdkFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUp() {
    jdkFixture = MavenProjectJDKTestFixture(maven.project, "MavenExecutionTestJDK")
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.setUp() })
    })
  }

  @AfterEach
  fun tearDownJdk() {
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.tearDown() })
    })
  }

  @Test
  fun testDoesNotMavenRunConfiguration() {
    val runner = ProgramRunner.findRunnerById(DELEGATE_RUNNER_ID)?: throw AssertionError("[$DELEGATE_RUNNER_ID] build runner should be registered")
    val mavenFactory = MavenRunConfigurationType.getInstance().configurationFactories[0]
    val mavenConfiguration = mavenFactory.createTemplateConfiguration(maven.project)

    assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, mavenConfiguration), "Delegate build runner must accept Maven run configuration with DefaultRunExecutor")
  }

  @Test
  fun testDoesNotAcceptGenericApplicationRunConfiguration() {
    val runner = ProgramRunner.findRunnerById(DELEGATE_RUNNER_ID) ?: throw AssertionError("[$DELEGATE_RUNNER_ID] build runner should be registered")
    val appFactory = ApplicationConfigurationType.getInstance().configurationFactories[0]
    val appConfiguration = appFactory.createTemplateConfiguration(maven.project)

    assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, appConfiguration), "Delegate build runner must not accept generic Application run configuration")
  }
}
