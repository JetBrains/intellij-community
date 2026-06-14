// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.localquarantine.plugin.importing

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import com.intellij.util.WaitFor
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.compileModules
import org.jetbrains.idea.maven.plugins.compatibility.PluginCompatibilityConfiguratorService
import org.jetbrains.idea.maven.tasks.MavenTasksManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenLifecyclePluginImportIntegrationTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private lateinit var myFixture: MavenProjectJDKTestFixture

  public @BeforeEach
  fun setUp() {
    myFixture = MavenProjectJDKTestFixture(maven.project, JDK_NAME)
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { myFixture.setUp() })
    })

  }

  public @AfterEach
  fun tearDown() {
    RunAll.runAll(
      {
        EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
          WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { myFixture.tearDown() })

        })
      },
    )
  }

  companion object {
    private const val JDK_NAME = "MavenExecutionTestJDK"
  }

  @Test
  fun testCreateGoalsAfterSync() = runBlocking {
    setupProjectWithMavenLifecycle("first")

    maven.importProjectAsync()
    runAndWaitForConfiguration()


    val mavenTasksManager = MavenTasksManager.getInstance(maven.project)
    val tasks = mavenTasksManager.getTasks(MavenTasksManager.Phase.BEFORE_COMPILE)
    assertSize(1, tasks)
    assertEquals("com.intellij.mavenplugin:maven-plugin-test-lifecycle:1.0:first", tasks.first().goal)
  }

  @Test
  fun testShouldNotDuplicateGoalsAfterReSync() = runBlocking {
    setupProjectWithMavenLifecycle("first")

    maven.importProjectAsync()
    runAndWaitForConfiguration()

    maven.importProjectAsync()
    runAndWaitForConfiguration()

    val mavenTasksManager = MavenTasksManager.getInstance(maven.project)
    val tasks = mavenTasksManager.getTasks(MavenTasksManager.Phase.BEFORE_COMPILE)
    assertSize(1, tasks)
    assertEquals("com.intellij.mavenplugin:maven-plugin-test-lifecycle:1.0:first", tasks.first().goal)
  }

  @Test
  fun testShouldRunGoalAfterSync() = runBlocking {
    setupProjectWithMavenLifecycle("second")

    maven.importProjectAsync()
    runAndWaitForConfiguration()


    val waitFor = object : WaitFor(15_000, 1_000) {
      override fun condition(): Boolean {
        val createdFile = maven.projectPath.resolve("second.txt")
        return createdFile.isRegularFile()
      }
    }

    assertEquals("second", maven.projectPath.resolve("second.txt").readText())
  }

  @Test
  fun testShouldRunGoalBeforeCompile() = runBlocking {
    setupProjectWithMavenLifecycle("first")

    maven.importProjectAsync()
    runAndWaitForConfiguration()

    maven.compileModules("project")

    val createdFile = maven.projectPath.resolve("first.txt")
    assertTrue(createdFile.isRegularFile())
    assertEquals("first", createdFile.readText())
  }

  private fun setupProjectWithMavenLifecycle(goal: String) {
    val helper = MavenCustomRepositoryHelper(maven.dir, "plugins")
    val repoPath = helper.getTestData("plugins")
    maven.repositoryPath = repoPath
    maven.projectsManager.importingSettings.isRunPluginsCompatibilityOnSyncAndBuild = true
    maven.createProjectPom("""
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <build>
          <plugins>
              <plugin>
                  <groupId>com.intellij.mavenplugin</groupId>
                  <artifactId>maven-plugin-test-lifecycle</artifactId>
                  <version>1.0</version>
                  <executions>
                    <execution>
                      <goals>
                        <goal>$goal</goal>
                      </goals>
                  </execution>
                </executions>
              </plugin>
          </plugins>
        </build>
      """)
  }

  private suspend fun runAndWaitForConfiguration() {
    //we cannot get rid of this - as this configurator runs after sync. It is a interesting question where to show this test
    maven.project.service<PluginCompatibilityConfiguratorService>().configureAsync()
  }

}