// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.maven.delegation.junit.MavenJUnitConfigurationExecutionEnvironmentProvider
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.task.ExecuteRunConfigurationTask
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.fixtures.execute
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class MavenJUnitDelegatedTestReactorTest {

  private val maven by mavenImportingFixture(skipPluginResolution = false)
  private lateinit var jdkFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUp() {
    jdkFixture = MavenProjectJDKTestFixture(maven.project, "MavenJUnitDelegatedTestReactorTestJDK")
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException> { jdkFixture.setUp() }
    }
  }

  @AfterEach
  fun tearDownJdk() {
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException> { jdkFixture.tearDown() }
    }
  }

  @Test
  fun `delegated JUnit test recompiles upstream instead of using installed artifact`() = runBlocking {
    createReactorProject()
    maven.repositoryPath = maven.projectPath.resolve("maven-repository")
    maven.importProjectAsync()

    val upstreamSource = """
      package probe;

      public class Upstream {
        public static String value() {
          return "old";
        }
      }
    """.trimIndent()
    maven.createProjectSubFile("upstream/src/main/java/probe/Upstream.java", upstreamSource)

    val installResult = maven.execute(MavenRunnerParameters(
      true,
      maven.projectPath.toCanonicalPath(),
      "pom.xml",
      mutableListOf("-DskipTests", "install"),
      emptyList(),
    ))
    assertTrue(installResult.stdout.contains("BUILD SUCCESS"), installResult.stdout)

    maven.createProjectSubFile("upstream/src/main/java/probe/Upstream.java", upstreamSource.replace("return \"old\";", "return missingSymbol;"))

    val leafModule = maven.getModule("leaf")
    val junitConfiguration = JUnitConfiguration("LeafTest", maven.project).apply {
      setModule(leafModule)
      persistentData.TEST_OBJECT = JUnitConfiguration.TEST_CLASS
      persistentData.MAIN_CLASS_NAME = "probe.LeafTest"
    }
    val executionEnvironment = withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        MavenJUnitConfigurationExecutionEnvironmentProvider().createExecutionEnvironment(
          maven.project,
          executionTask(junitConfiguration),
          null,
        )
      }
    }
    assertNotNull(executionEnvironment)

    val delegatedParameters = (executionEnvironment!!.runProfile as MavenSurefireRunConfiguration).runnerParameters
    assertEquals(maven.projectPath.toCanonicalPath(), delegatedParameters.workingDirPath)
    assertEquals("pom.xml", delegatedParameters.pomFileName)
    assertEquals(
      listOf(
        "--projects=probe:leaf",
        "--also-make",
        "-Dtest=probe.LeafTest",
        "-DfailIfNoTests=false",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "test",
      ),
      delegatedParameters.goals,
    )

    // Mirrors the historical JUnit provider: run the leaf POM without workspace resolution or a reactor.
    val legacyLeafParameters = MavenRunnerParameters(
      true,
      maven.projectPath.resolve("leaf").toCanonicalPath(),
      "pom.xml",
      mutableListOf("-Dtest=probe.LeafTest", "-DfailIfNoTests=false", "test"),
      emptyList(),
    )
    assertFalse(legacyLeafParameters.isResolveToWorkspace)
    val staleLeafResult = maven.execute(legacyLeafParameters)
    assertTrue(staleLeafResult.stdout.contains("Running probe.LeafTest"), staleLeafResult.stdout)
    assertTrue(staleLeafResult.stdout.contains("BUILD SUCCESS"), staleLeafResult.stdout)

    val delegatedResult = maven.execute(delegatedParameters)
    assertTrue(delegatedResult.stdout.contains("BUILD FAILURE"), delegatedResult.stdout)
    assertTrue(delegatedResult.stdout.contains("cannot find symbol"), delegatedResult.stdout)
  }

  private fun createReactorProject() {
    maven.createProjectPom("""
      <groupId>probe</groupId>
      <artifactId>root</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
      </properties>
      <modules>
        <module>upstream</module>
        <module>leaf</module>
      </modules>
    """.trimIndent())
    maven.createModulePom("upstream", """
      <parent>
        <groupId>probe</groupId>
        <artifactId>root</artifactId>
        <version>1</version>
      </parent>
      <artifactId>upstream</artifactId>
    """.trimIndent())
    maven.createModulePom("leaf", """
      <parent>
        <groupId>probe</groupId>
        <artifactId>root</artifactId>
        <version>1</version>
      </parent>
      <artifactId>leaf</artifactId>
      <dependencies>
        <dependency>
          <groupId>probe</groupId>
          <artifactId>upstream</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
          <scope>test</scope>
        </dependency>
      </dependencies>
    """.trimIndent())
    maven.createProjectSubFile("leaf/src/test/java/probe/LeafTest.java", """
      package probe;

      import org.junit.Test;

      public class LeafTest {
        @Test
        public void usesInstalledUpstreamArtifact() {
          if (!"old".equals(Upstream.value())) {
            throw new AssertionError("unexpected upstream artifact");
          }
        }
      }
    """.trimIndent())
  }

  private fun executionTask(configuration: JUnitConfiguration): ExecuteRunConfigurationTask = object : ExecuteRunConfigurationTask {
    override fun getRunProfile(): JUnitConfiguration = configuration
    override fun getExecutionTarget(): ExecutionTarget? = null
    override fun getRunnerSettings(): RunnerSettings? = null
    override fun getConfigurationSettings(): ConfigurationPerRunnerSettings? = null
    override fun getSettings(): RunnerAndConfigurationSettings? = null
    override fun getPresentableName(): String = configuration.name
  }
}
