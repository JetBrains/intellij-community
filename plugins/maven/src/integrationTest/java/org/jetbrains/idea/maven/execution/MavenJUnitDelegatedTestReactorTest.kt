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
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
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
    // Filter out the dynamic -Dsurefire.reportNameSuffix=<timestamp> entry before comparing.
    val staticGoals = delegatedParameters.goals.filter { !it.startsWith("-Dsurefire.reportNameSuffix=") }
    assertEquals(
      listOf(
        "--projects=probe:leaf",
        "--also-make",
        "-Dtest=probe.LeafTest",
        "-DfailIfNoTests=false",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "test",
      ),
      staticGoals,
    )
    assertTrue(delegatedParameters.goals.any { it.startsWith("-Dsurefire.reportNameSuffix=") })

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

  // IDEA-394511: Surefire reads a final `.*` of a -Dtest filter as "any file extension", so the filter `probe.*` selects
  // the class `probe` and no test runs. These tests run Maven to make sure that Surefire runs the selected tests.

  @Test
  fun `delegated JUnit package run runs the tests of the package and of its subpackages`() = runBlocking {
    createPackageProject()

    val stdout = runDelegated {
      TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE
      PACKAGE_NAME = "probe"
    }

    assertTrue(stdout.contains("Running probe.LeafTest"), stdout)
    assertTrue(stdout.contains("Running probe.sub.SubTest"), stdout)
  }

  @Test
  fun `delegated JUnit directory run runs the tests of the directory and of its subdirectories`() = runBlocking {
    createPackageProject()

    val stdout = runDelegated {
      TEST_OBJECT = JUnitConfiguration.TEST_DIRECTORY
      dirName = maven.projectPath.resolve("leaf/src/test/java/probe").toCanonicalPath()
    }

    assertTrue(stdout.contains("Running probe.LeafTest"), stdout)
    assertTrue(stdout.contains("Running probe.sub.SubTest"), stdout)
  }

  // IDEA-396099: a delegated test run must give Maven the profiles that are checked in the Maven tool window, as a
  // delegated build does. Here only the profile `needed` gives the module `leaf` its dependency on `upstream`, so the
  // tests of `leaf` do not compile without the profile.
  @Test
  fun `delegated JUnit run uses the checked Maven profiles`() = runBlocking {
    createReactorProject()
    maven.createModulePom("leaf", """
      <parent>
        <groupId>probe</groupId>
        <artifactId>root</artifactId>
        <version>1</version>
      </parent>
      <artifactId>leaf</artifactId>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.13.2</version>
          <scope>test</scope>
        </dependency>
      </dependencies>
      <profiles>
        <profile>
          <id>needed</id>
          <dependencies>
            <dependency>
              <groupId>probe</groupId>
              <artifactId>upstream</artifactId>
              <version>1</version>
            </dependency>
          </dependencies>
        </profile>
      </profiles>
    """.trimIndent())
    maven.repositoryPath = maven.projectPath.resolve("maven-repository")
    maven.createProjectSubFile("upstream/src/main/java/probe/Upstream.java", """
      package probe;

      public class Upstream {
        public static String value() {
          return "old";
        }
      }
    """.trimIndent())
    maven.importProjectAsync()
    // After the import: the import of the test fixture sets the explicit profiles to none.
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("needed"))

    val stdout = runDelegated {
      TEST_OBJECT = JUnitConfiguration.TEST_CLASS
      MAIN_CLASS_NAME = "probe.LeafTest"
    }

    assertTrue(stdout.contains("Running probe.LeafTest"), stdout)
  }

  private suspend fun createPackageProject() {
    createReactorProject()
    maven.repositoryPath = maven.projectPath.resolve("maven-repository")
    maven.createProjectSubFile("upstream/src/main/java/probe/Upstream.java", """
      package probe;

      public class Upstream {
        public static String value() {
          return "old";
        }
      }
    """.trimIndent())
    maven.createProjectSubFile("leaf/src/test/java/probe/sub/SubTest.java", """
      package probe.sub;

      import org.junit.Test;

      public class SubTest {
        @Test
        public void runs() {
        }
      }
    """.trimIndent())
    maven.importProjectAsync()
  }

  /** Builds the delegated Maven run of a JUnit configuration of the module `leaf`, runs it, and returns its output. */
  private suspend fun runDelegated(configure: JUnitConfiguration.Data.() -> Unit): String {
    val junitConfiguration = JUnitConfiguration("ProbeTests", maven.project).apply {
      setModule(maven.getModule("leaf"))
      persistentData.configure()
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
    val result = maven.execute(delegatedParameters)
    assertTrue(result.stdout.contains("BUILD SUCCESS"), result.stdout)
    return result.stdout
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
          <version>4.13.2</version>
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
