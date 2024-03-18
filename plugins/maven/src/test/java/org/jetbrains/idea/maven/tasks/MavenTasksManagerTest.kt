// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.maven.testFramework.MavenCompilingTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.junit.Test

class MavenTasksManagerTest : MavenCompilingTestCase() {
  @Test
  fun `test run execute before build tasks`() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val parametersList = mutableListOf<MavenRunnerParameters>()
    subscribeToMavenGoalExecution("clean", parametersList)
    addCompileTask(projectPom.path, "clean")
    compileModules("project")
    assertSize(1, parametersList)
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
    val parametersList = mutableListOf<MavenRunnerParameters>()
    subscribeToMavenGoalExecution("generate-sources", parametersList)
    addCompileTask(m1File.path, "generate-sources")
    compileModules("m1")
    assertSize(1, parametersList)
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
    val parametersList = mutableListOf<MavenRunnerParameters>()
    subscribeToMavenGoalExecution("generate-sources", parametersList)
    addCompileTask(m1File.path, "generate-sources")
    compileModules("m2")
    assertSize(0, parametersList)
  }

  @Test
  fun `test group tasks by goal` () = runBlocking {
    var p = createProjectPom("""
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
    val parametersList = mutableListOf<MavenRunnerParameters>()
    subscribeToMavenGoalExecution("generate-sources", parametersList)
    addCompileTask(m1File.path, "generate-sources")
    addCompileTask(m2File.path, "generate-sources")
    compileModules("m1", "m2")

    assertSize(1, parametersList)
    val parameters = parametersList[0]
    assertEquals(p.path, parameters.workingDirPath + "/" + parameters.pomFileName)
    assertEquals(setOf("group:m1", "group:m2"), parameters.projectsCmdOptionValues.toSet())
  }

  private fun addCompileTask(pomPath: String, goal: String) {
    val mavenTasksManager = MavenTasksManager.getInstance(project)
    val task = MavenCompilerTask(pomPath, goal)
    mavenTasksManager.addCompileTasks(listOf(task), MavenTasksManager.Phase.BEFORE_COMPILE)
  }

  private fun subscribeToMavenGoalExecution(goal: String, parametersList: MutableList<MavenRunnerParameters>) {
    val connection = project.messageBus.connect()
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processStartScheduled(executorIdLocal: String, environmentLocal: ExecutionEnvironment) {
        val runProfile = environmentLocal.runProfile
        if (runProfile is MavenRunConfiguration) {
          parametersList.add(runProfile.runnerParameters)
        }
      }
    })
  }
}
