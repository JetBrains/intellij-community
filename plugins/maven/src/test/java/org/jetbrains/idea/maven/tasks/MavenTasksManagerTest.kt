// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.maven.testFramework.MavenCompilingTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.junit.Test

class MavenTasksManagerTest : MavenCompilingTestCase() {
  @Test
  fun `test run execute before build tasks`() = runBlocking {
    createProjectSubDirs(".mvn") // for Maven to detect root project
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val processResults = mutableListOf<ProcessResult>()
    subscribeToMavenGoalExecution("clean", processResults)
    addCompileTask(projectPom.path, "clean")
    compileModulesAndAssertExitCode(processResults, "project")
  }

  @Test
  fun `test run execute before build tasks in the same module`() = runBlocking {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())

    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    val m2File = createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    importProjectAsync()
    val processResults = mutableListOf<ProcessResult>()
    subscribeToMavenGoalExecution("generate-sources", processResults)
    addCompileTask(m1File.path, "generate-sources")
    compileModulesAndAssertExitCode(processResults, "m1")
  }

  @Test
  fun `test don't run execute before build tasks in another module` () = runBlocking {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())

    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    val m2File = createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    importProjectAsync()
    val processResults = mutableListOf<ProcessResult>()
    subscribeToMavenGoalExecution("generate-sources", processResults)
    addCompileTask(m1File.path, "generate-sources")
    compileModules("m2")
    assertSize(0, processResults)
  }

  @Test
  fun `test group tasks by goal` () = runBlocking {
    createProjectSubDirs(".mvn") // for Maven to detect root project
    val p = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())

    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    val m2File = createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())

    importProjectAsync()
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
    importProjectAsync("""
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
    addCompileTask(projectPom.path, oldGoal)

    val tasksManager = MavenTasksManager.getInstance(project)
    assertTrue(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(projectPom.path, oldGoal),
                                                 MavenTasksManager.Phase.BEFORE_COMPILE))

    // Update plugin version and re-sync
    importProjectAsync("""
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
    assertFalse("Stale task with old version should be gone",
                tasksManager.isCompileTaskOfPhase(MavenCompilerTask(projectPom.path, oldGoal),
                                                  MavenTasksManager.Phase.BEFORE_COMPILE))
    assertTrue("Task with updated version should exist",
               tasksManager.isCompileTaskOfPhase(MavenCompilerTask(projectPom.path, newGoal),
                                                 MavenTasksManager.Phase.BEFORE_COMPILE))
  }

  @Test
  fun `test qualified task goal is removed when plugin is removed from project`() = runBlocking {
    // maven-assembly-plugin has no default lifecycle binding, so it is truly absent when removed from the POM
    importProjectAsync("""
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
    addCompileTask(projectPom.path, goal)

    val tasksManager = MavenTasksManager.getInstance(project)
    assertTrue(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(projectPom.path, goal),
                                                 MavenTasksManager.Phase.BEFORE_COMPILE))

    // Remove the plugin declaration and re-sync; the plugin has no default binding so it disappears from the model
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertFalse("Task for removed plugin should be dropped",
                tasksManager.isCompileTaskOfPhase(MavenCompilerTask(projectPom.path, goal),
                                                  MavenTasksManager.Phase.BEFORE_COMPILE))
  }

  @Test
  fun `test unqualified task goals are not changed after sync`() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    addCompileTask(projectPom.path, "clean")

    importProjectAsync("""
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

    assertTrue(MavenTasksManager.getInstance(project)
                 .isCompileTaskOfPhase(MavenCompilerTask(projectPom.path, "clean"),
                                       MavenTasksManager.Phase.BEFORE_COMPILE))
  }

  @Test
  fun `test tasks are dropped when maven project is removed from structure`() = runBlocking {
    createProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())
    val m1File = createModulePom("m1", """
                    <artifactId>m1</artifactId>
                    <version>1</version>
                    <parent>
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                    </parent>
                    """.trimIndent())
    importProjectAsync()

    val tasksManager = MavenTasksManager.getInstance(project)
    addCompileTask(m1File.path, "clean")
    assertTrue(tasksManager.isCompileTaskOfPhase(MavenCompilerTask(m1File.path, "clean"),
                                                 MavenTasksManager.Phase.BEFORE_COMPILE))

    // Remove m1 from parent modules list, re-sync
    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    """.trimIndent())
    importProjectAsync()

    assertFalse("Task for removed project should be dropped",
                tasksManager.isCompileTaskOfPhase(MavenCompilerTask(m1File.path, "clean"),
                                                  MavenTasksManager.Phase.BEFORE_COMPILE))
  }

  private fun addCompileTask(pomPath: String, goal: String) {
    val mavenTasksManager = MavenTasksManager.getInstance(project)
    val task = MavenCompilerTask(pomPath, goal)
    mavenTasksManager.addCompileTasks(listOf(task), MavenTasksManager.Phase.BEFORE_COMPILE)
  }

  private fun subscribeToMavenGoalExecution(goal: String, processResults: MutableList<ProcessResult>) {
    val connection = project.messageBus.connect()
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
      compileModules(*moduleNames)
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
    assertEquals("command failed with exit code $exitCode:\n$commandLine\n", 0, exitCode)
  }

  private data class ProcessResult(val runnerParameters: MavenRunnerParameters, val commandLine: String, val exitCode: Int)
}
