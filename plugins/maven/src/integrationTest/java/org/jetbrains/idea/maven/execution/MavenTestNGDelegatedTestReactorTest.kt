// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.maven.delegation.testng.MavenTestNGConfigurationExecutionEnvironmentProvider
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.task.ExecuteRunConfigurationTask
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.theoryinpractice.testng.configuration.TestNGConfiguration
import com.theoryinpractice.testng.model.TestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration test for [MavenTestNGConfigurationExecutionEnvironmentProvider]:
 * verifies that the correct Maven reactor parameters (`--projects`, `--also-make`, `-Dtest=`)
 * are assembled for each supported TestNG test type.
 *
 * Parallel to [MavenJUnitDelegatedTestReactorTest] for the JUnit side.
 */
@TestApplication
class MavenTestNGDelegatedTestReactorTest {

  private val maven by mavenImportingFixture(skipPluginResolution = false)
  private lateinit var jdkFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUp() {
    jdkFixture = MavenProjectJDKTestFixture(maven.project, "MavenTestNGDelegatedTestReactorTestJDK")
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException> { jdkFixture.setUp() }
    }
  }

  @AfterEach
  fun tearDown() {
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException> { jdkFixture.tearDown() }
    }
  }

  // ── CLASS ─────────────────────────────────────────────────────────────────

  @Test
  fun `CLASS type produces correct reactor parameters`() = runBlocking {
    createProject()
    maven.importProjectAsync()

    val goals = delegatedGoals(TestType.CLASS) {
      persistantData.MAIN_CLASS_NAME = "probe.MyTest"
    }

    // Filter out the dynamic -Dsurefire.reportNameSuffix=<timestamp> entry before comparing.
    val staticGoals = goals.filter { !it.startsWith("-Dsurefire.reportNameSuffix=") }
    assertEquals(
      listOf(
        "--projects=probe:probe",
        "--also-make",
        "-Dtest=probe.MyTest",
        "-DfailIfNoTests=false",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "test",
      ),
      staticGoals,
    )
    assertTrue(goals.any { it.startsWith("-Dsurefire.reportNameSuffix=") })
  }

  // ── METHOD ────────────────────────────────────────────────────────────────

  @Test
  fun `METHOD type produces ClassName#methodName filter`() = runBlocking {
    createProject()
    maven.importProjectAsync()

    val goals = delegatedGoals(TestType.METHOD) {
      persistantData.MAIN_CLASS_NAME = "probe.MyTest"
      persistantData.METHOD_NAME = "myMethod"
    }

    assertTrue(goals.contains("-Dtest=probe.MyTest#myMethod"), "goals=$goals")
  }

  // ── PACKAGE ───────────────────────────────────────────────────────────────

  @Test
  fun `PACKAGE type produces pkg-star filter`() = runBlocking {
    createProject()
    maven.importProjectAsync()

    val goals = delegatedGoals(TestType.PACKAGE) {
      persistantData.PACKAGE_NAME = "probe.sub"
    }

    assertTrue(goals.contains("-Dtest=probe.sub.*"), "goals=$goals")
  }

  @Test
  fun `PACKAGE type with empty package produces star filter`() = runBlocking {
    createProject()
    maven.importProjectAsync()

    val goals = delegatedGoals(TestType.PACKAGE) {
      persistantData.PACKAGE_NAME = ""
    }

    assertTrue(goals.contains("-Dtest=*"), "goals=$goals")
  }

  // ── PATTERN ───────────────────────────────────────────────────────────────

  @Test
  fun `PATTERN type joins patterns with plus`() = runBlocking {
    createProject()
    maven.importProjectAsync()

    val goals = delegatedGoals(TestType.PATTERN) {
      persistantData.patterns.add("probe.ATest")
      persistantData.patterns.add("probe.BTest")
    }

    assertTrue(goals.contains("-Dtest=probe.ATest+probe.BTest"), "goals=$goals")
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private fun createProject() {
    maven.createProjectPom("""
      <groupId>probe</groupId>
      <artifactId>probe</artifactId>
      <version>1</version>
      <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
      </properties>
      <dependencies>
        <dependency>
          <groupId>org.testng</groupId>
          <artifactId>testng</artifactId>
          <version>7.9.0</version>
          <scope>test</scope>
        </dependency>
      </dependencies>
    """.trimIndent())
    maven.repositoryPath = maven.projectPath.resolve("maven-repository")
  }

  private suspend fun delegatedGoals(
    type: TestType,
    configure: TestNGConfiguration.() -> Unit,
  ): List<String> {
    val env = createEnvironment(type, configure)
    assertNotNull(env, "createExecutionEnvironment returned null for $type")
    return (env!!.runProfile as MavenSurefireRunConfiguration).runnerParameters.goals
  }

  private suspend fun createEnvironment(
    type: TestType,
    configure: TestNGConfiguration.() -> Unit,
  ) = withContext(Dispatchers.EDT) {
    writeIntentReadAction {
      val module = maven.getModule("probe")
      val config = TestNGConfiguration("probe-test", maven.project).apply {
        setModule(module)
        persistantData.TEST_OBJECT = type.type
        configure()
      }
      MavenTestNGConfigurationExecutionEnvironmentProvider().createExecutionEnvironment(
        maven.project,
        executionTask(config),
        null,
      )
    }
  }

  private fun executionTask(configuration: TestNGConfiguration): ExecuteRunConfigurationTask =
    object : ExecuteRunConfigurationTask {
      override fun getRunProfile(): TestNGConfiguration = configuration
      override fun getExecutionTarget(): ExecutionTarget? = null
      override fun getRunnerSettings(): RunnerSettings? = null
      override fun getConfigurationSettings(): ConfigurationPerRunnerSettings? = null
      override fun getSettings(): RunnerAndConfigurationSettings? = null
      override fun getPresentableName(): String = configuration.name
    }
}
