// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.compatibility

import com.intellij.maven.testFramework.MavenCompilingTestCase
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.WaitFor
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.execution.ScriptMavenExecutionTest.Companion.wrapperOutput
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.tasks.MavenTasksManager
import org.junit.Test
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * maven-plugin-test-lifecycle - see attached src.jar
 * provides two goals: first and second
 *  first goal runs on compilation only, second runs on configuration only
 *  this goals create first.txt and second.txt files in outputDieк
 */
class MavenLifecyclePluginImportingTest : MavenCompilingTestCase() {
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

    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper

    runAndWaitForConfiguration()


    object : WaitFor(20_000, 1_000) {
      override fun condition(): Boolean {
        val createdFile = projectPath.resolve("parameters.wrapper.txt")
        return createdFile.isRegularFile()
      }
    }.join()

    val text = projectPath.resolve("parameters.wrapper.txt").readText().trimEnd()
    assertTrue(text, text.endsWith("com.intellij.mavenplugin:maven-plugin-test-lifecycle:1.0:second -f pom.xml"))
  }

  @Test
  fun testShouldRunGoalBeforeCompile() = runBlocking {
    setupProjectWithMavenLifecycle("first")

    importProjectAsync()
    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    runAndWaitForConfiguration()
    assertModules("project")
    createProjectSubFile("src/main/java/Main.java", """public class Main{}""")

    rebuildProject()
    val createdFile = projectPath.resolve("parameters.wrapper.txt")
    assertTrue(createdFile.isRegularFile())
    val text = createdFile.readText().trimEnd()
    assertTrue(text, text.endsWith("com.intellij.mavenplugin:maven-plugin-test-lifecycle:1.0:first -f pom.xml"))
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
    project.service<PluginCompatibilityConfiguratorService>().configureAsync()
  }

  private fun createFakeProjectWrapper() {
    createProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                         "distributionUrl=http://example.com")
    if (SystemInfo.isWindows) {
      createProjectSubFile("mvnw.cmd", "@echo $wrapperOutput\r\n@echo %* > .\\parameters.wrapper.txt\r\n@set > .\\env.wrapper.txt\r\n")
    }
    else {
      createProjectSubFile("mvnw", "#!/bin/sh\necho $wrapperOutput\necho $@ > ./parameters.wrapper.txt \nprintenv > ./env.wrapper.txt \n")
    }
  }
}