// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.localquarantine.plugin.importing

import com.intellij.maven.testFramework.MavenCompilingTestCase
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.WaitFor
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.plugins.compatibility.PluginCompatibilityConfigurator
import org.jetbrains.idea.maven.tasks.MavenTasksManager
import org.junit.Test
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class MavenLifecyclePluginImportIntegrationTest : MavenCompilingTestCase() {
  private lateinit var myFixture: MavenProjectJDKTestFixture

  public override fun setUp() {
    super.setUp()
    myFixture = MavenProjectJDKTestFixture(project, JDK_NAME)
    edt<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { myFixture.setUp() })
    })

  }

  public override fun tearDown() {
    RunAll.runAll(
      {
        edt<RuntimeException?>(ThrowableRunnable {
          WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { myFixture.tearDown() })

        })
      },
      { super.tearDown() }
    )
  }

  companion object {
    private const val JDK_NAME = "MavenExecutionTestJDK"
  }

  @Test
  fun testCreateGoalsAfterSync() = runBlocking {
    setupProjectWithMavenLifecycle("first")

    importProjectAsync()
    runAndWaitForConfiguration()


    val mavenTasksManager = MavenTasksManager.getInstance(project)
    val tasks = mavenTasksManager.getTasks(MavenTasksManager.Phase.BEFORE_COMPILE)
    assertSize(1, tasks)
    assertEquals("com.intellij.mavenplugin:maven-plugin-test-lifecycle:1.0:first", tasks.first().goal)
  }

  @Test
  fun testShouldNotDuplicateGoalsAfterReSync() = runBlocking {
    setupProjectWithMavenLifecycle("first")

    importProjectAsync()
    runAndWaitForConfiguration()

    importProjectAsync()
    runAndWaitForConfiguration()

    val mavenTasksManager = MavenTasksManager.getInstance(project)
    val tasks = mavenTasksManager.getTasks(MavenTasksManager.Phase.BEFORE_COMPILE)
    assertSize(1, tasks)
    assertEquals("com.intellij.mavenplugin:maven-plugin-test-lifecycle:1.0:first", tasks.first().goal)
  }

  @Test
  fun testShouldRunGoalAfterSync() = runBlocking {
    setupProjectWithMavenLifecycle("second")

    importProjectAsync()
    runAndWaitForConfiguration()


    val waitFor = object : WaitFor(15_000, 1_000) {
      override fun condition(): Boolean {
        val createdFile = projectPath.resolve("second.txt")
        return createdFile.isRegularFile()
      }
    }

    assertEquals("second", projectPath.resolve("second.txt").readText())
  }

  @Test
  fun testShouldRunGoalBeforeCompile() = runBlocking {
    setupProjectWithMavenLifecycle("first")

    importProjectAsync()
    runAndWaitForConfiguration()

    compileModules("project")

    val createdFile = projectPath.resolve("first.txt")
    assertTrue(createdFile.isRegularFile())
    assertEquals("first", createdFile.readText())
  }

  private fun setupProjectWithMavenLifecycle(goal: String) {
    val helper = MavenCustomRepositoryHelper(dir, "plugins")
    val repoPath = helper.getTestData("plugins")
    repositoryPath = repoPath
    projectsManager.importingSettings.isRunPluginsCompatibilityOnSyncAndBuild = true
    createProjectPom("""
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
    project.service<PluginCompatibilityConfigurator>().configureAsync()
  }

}