// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.fixtures.compileModules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenTasksManagerTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun `test run execute before build tasks`() = runBlocking {
    maven.createProjectSubDirs(".mvn") // for Maven to detect root project
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val processResults = mutableListOf<ProcessResult>()
    subscribeToMavenGoalExecution("clean", processResults)
    addCompileTask(maven.projectPom.path, "clean")
    compileModulesAndAssertExitCode(processResults, "project")
  }

  @Test
  fun `test run execute before build tasks in the same module`() = runBlocking {
    maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())

    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    val m2File = maven.createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    maven.importProjectAsync()
    val processResults = mutableListOf<ProcessResult>()
    subscribeToMavenGoalExecution("generate-sources", processResults)
    addCompileTask(m1File.path, "generate-sources")
    compileModulesAndAssertExitCode(processResults, "m1")
  }

  @Test
  fun `test don't run execute before build tasks in another module` () = runBlocking {
    maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())

    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    val m2File = maven.createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    maven.importProjectAsync()
    val processResults = mutableListOf<ProcessResult>()
    subscribeToMavenGoalExecution("generate-sources", processResults)
    addCompileTask(m1File.path, "generate-sources")
    maven.compileModules("m2")
    assertSize(0, processResults)
  }

  @Test
  fun `test group tasks by goal` () = runBlocking {
    maven.createProjectSubDirs(".mvn") // for Maven to detect root project
    val p = maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())

    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    val m2File = maven.createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    maven.importProjectAsync()
    val processResults = mutableListOf<ProcessResult>()
    subscribeToMavenGoalExecution("generate-sources", processResults)
    addCompileTask(m1File.path, "generate-sources")
    addCompileTask(m2File.path, "generate-sources")
    compileModulesAndAssertExitCode(processResults, "m1", "m2")

    val parameters = processResults[0].runnerParameters
    assertEquals(p.path, parameters.workingDirPath + "/" + parameters.pomFileName)
    assertEquals(setOf("group:m1", "group:m2"), parameters.projectsCmdOptionValues.toSet())
  }

  @Test
  fun `test task goal version is updated after plugin version change`() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.8.1</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    val oldGoal = "org.apache.maven.plugins:maven-compiler-plugin:3.8.1:compile"
    addCompileTask(maven.projectPom.path, oldGoal)

    val tasksManager = MavenTasksManager.getInstance(maven.project)
    assertTrue(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(maven.projectPom.path, oldGoal),
                                                 MavenTasksManager.Phase.BEFORE_COMPILE))

    // Update plugin version and re-sync
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.8.2</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    val newGoal = "org.apache.maven.plugins:maven-compiler-plugin:3.8.2:compile"
    assertFalse(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(maven.projectPom.path, oldGoal),
                                                  MavenTasksManager.Phase.BEFORE_COMPILE),
                "Stale task with old version should be gone")
    assertTrue(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(maven.projectPom.path, newGoal),
                                                 MavenTasksManager.Phase.BEFORE_COMPILE),
               "Task with updated version should exist")
  }

  @Test
  fun `test qualified task goal is removed when plugin is removed from project`() = runBlocking {
    // maven-assembly-plugin has no default lifecycle binding, so it is truly absent when removed from the POM
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-assembly-plugin</artifactId>
                          <version>3.3.0</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    val goal = "org.apache.maven.plugins:maven-assembly-plugin:3.3.0:single"
    addCompileTask(maven.projectPom.path, goal)

    val tasksManager = MavenTasksManager.getInstance(maven.project)
    assertTrue(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(maven.projectPom.path, goal),
                                                 MavenTasksManager.Phase.BEFORE_COMPILE))

    // Remove the plugin declaration and re-sync; the plugin has no default binding so it disappears from the model
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertFalse(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(maven.projectPom.path, goal),
                                                  MavenTasksManager.Phase.BEFORE_COMPILE),
                "Task for removed plugin should be dropped")
  }

  @Test
  fun `test unqualified task goals are not changed after sync`() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    addCompileTask(maven.projectPom.path, "clean")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.8.2</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertTrue(MavenTasksManager.getInstance(maven.project)
                 .isCompileTaskOfPhase(MavenCompilerTask(maven.projectPom.path, "clean"),
                                       MavenTasksManager.Phase.BEFORE_COMPILE))
  }

  @Test
  fun `test tasks are dropped when maven project is removed from structure`() = runBlocking {
    maven.createProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                    <artifactId>m1</artifactId>
                    <version>1</version>
                    <parent>
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                    </parent>
                    """.trimIndent())
    maven.importProjectAsync()

    val tasksManager = MavenTasksManager.getInstance(maven.project)
    addCompileTask(m1File.path, "clean")
    assertTrue(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(m1File.path, "clean"),
                                                 MavenTasksManager.Phase.BEFORE_COMPILE))

    // Remove m1 from parent modules list, re-sync
    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    """.trimIndent())
    maven.importProjectAsync()

    assertFalse(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(m1File.path, "clean"),
                                                  MavenTasksManager.Phase.BEFORE_COMPILE),
                "Task for removed project should be dropped")
  }

  private fun addCompileTask(pomPath: String, goal: String) {
    val mavenTasksManager = MavenTasksManager.getInstance(maven.project)
    val task = MavenCompilerTask(pomPath, goal)
    mavenTasksManager.addCompileTasks(listOf(task), MavenTasksManager.Phase.BEFORE_COMPILE)
  }

  private fun subscribeToMavenGoalExecution(goal: String, processResults: MutableList<ProcessResult>) {
    val connection = maven.project.messageBus.connect()
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processTerminated(executorIdLocal: String, environmentLocal: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        val runProfile = environmentLocal.runProfile
        if (runProfile is MavenRunConfiguration) {
          val commandLine = (handler as BaseProcessHandler<*>).commandLine
          processResults.add(ProcessResult(runProfile.runnerParameters, commandLine, exitCode))
        }
      }
    })
  }

  private suspend fun compileModulesAndAssertExitCode(processResults: MutableList<ProcessResult>, vararg moduleNames: String) {
    try {
      maven.compileModules(*moduleNames)
      assertExitCode(processResults)
    } catch (e: Throwable) {
      assertExitCode(processResults)
      throw e
    }
  }

  private fun assertExitCode(processResults: MutableList<ProcessResult>) {
    assertSize(1, processResults)
    val processResult = processResults[0]
    val exitCode = processResult.exitCode
    val commandLine = processResult.commandLine
    assertEquals(0, exitCode, "command failed with exit code $exitCode:\n$commandLine\n")
  }

  private data class ProcessResult(val runnerParameters: MavenRunnerParameters, val commandLine: String, val exitCode: Int)
}
