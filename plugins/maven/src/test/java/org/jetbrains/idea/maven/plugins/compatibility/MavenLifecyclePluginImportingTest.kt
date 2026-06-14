// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.compatibility

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import com.intellij.util.WaitFor
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.ScriptMavenExecutionTest.Companion.wrapperOutput
import org.jetbrains.idea.maven.fixtures.rebuildProject
import org.jetbrains.idea.maven.project.MavenWrapper
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

/**
 * maven-plugin-test-lifecycle - see attached src.jar
 * provides two goals: first and second
 *  first goal runs on compilation only, second runs on configuration only
 *  this goals create first.txt and second.txt files in outputDieк
 */
@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenLifecyclePluginImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )
  

  private lateinit var myFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUp() {
    myFixture = MavenProjectJDKTestFixture(maven.project, JDK_NAME)
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { myFixture.setUp() })
    })

  }

  @AfterEach
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

    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper

    runAndWaitForConfiguration()


    object : WaitFor(20_000, 1_000) {
      override fun condition(): Boolean {
        val createdFile = maven.projectPath.resolve("parameters.wrapper.txt")
        return createdFile.isRegularFile()
      }
    }.join()

    val text = maven.projectPath.resolve("parameters.wrapper.txt").readText().trimEnd()
    assertTrue(text.endsWith("com.intellij.mavenplugin:maven-plugin-test-lifecycle:1.0:second -f pom.xml"), text)
  }

  @Test
  fun testShouldRunGoalBeforeCompile() = runBlocking {
    setupProjectWithMavenLifecycle("first")

    maven.importProjectAsync()
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    runAndWaitForConfiguration()
    maven.assertModules("project")
    maven.createProjectSubFile("src/main/java/Main.java", """public class Main{}""")

    maven.rebuildProject()
    val createdFile = maven.projectPath.resolve("parameters.wrapper.txt")
    assertTrue(createdFile.isRegularFile())
    val text = createdFile.readText().trimEnd()
    assertTrue(text.endsWith("com.intellij.mavenplugin:maven-plugin-test-lifecycle:1.0:first -f pom.xml"), text)
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

  private fun createFakeProjectWrapper() {
    maven.createProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                         "distributionUrl=http://example.com")
    if (SystemInfo.isWindows) {
      maven.createProjectSubFile("mvnw.cmd", "@echo $wrapperOutput\r\n@echo %* > .\\parameters.wrapper.txt\r\n@set > .\\env.wrapper.txt\r\n")
    }
    else {
      maven.createProjectSubFile("mvnw", "#!/bin/sh\necho $wrapperOutput\necho $@ > ./parameters.wrapper.txt \nprintenv > ./env.wrapper.txt \n")
    }
  }
}