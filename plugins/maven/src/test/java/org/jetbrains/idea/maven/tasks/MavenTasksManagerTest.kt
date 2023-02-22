// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.maven.testFramework.MavenCompilingTestCase
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.junit.Test
import java.util.List
import java.util.concurrent.atomic.AtomicBoolean

class MavenTasksManagerTest : MavenCompilingTestCase() {
  @Test
  fun testRunExecuteBeforeBuildTasks() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    
                    """.trimIndent())
    val mavenGoalExecuted = AtomicBoolean(false)
    subscribeToMavenGoalExecution("clean", mavenGoalExecuted)
    addCompileTask(myProjectPom.path, "clean")
    compileModules("project")
    assertTrue(mavenGoalExecuted.get())
  }

  @Test
  fun testRunExecuteBeforeBuildTasksInTheSameModule() {
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
    importProject()
    val mavenGoalExecuted = AtomicBoolean(false)
    subscribeToMavenGoalExecution("generate-sources", mavenGoalExecuted)
    addCompileTask(m1File.path, "generate-sources")
    compileModules("m1")
    assertTrue(mavenGoalExecuted.get())
  }

  @Test
  fun testDontRunExecuteBeforeBuildTasksInAnotherModule() {
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
    importProject()
    val mavenGoalExecuted = AtomicBoolean(false)
    subscribeToMavenGoalExecution("generate-sources", mavenGoalExecuted)
    addCompileTask(m1File.path, "generate-sources")
    compileModules("m2")
    assertFalse(mavenGoalExecuted.get())
  }

  private fun addCompileTask(pomPath: String, goal: String) {
    val mavenTasksManager = MavenTasksManager.getInstance(myProject)
    val task = MavenCompilerTask(pomPath, goal)
    mavenTasksManager.addCompileTasks(List.of(task), MavenTasksManager.Phase.BEFORE_COMPILE)
  }

  private fun subscribeToMavenGoalExecution(goal: String, executionFlag: AtomicBoolean) {
    val connection = myProject.messageBus.connect()
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processStartScheduled(executorIdLocal: String, environmentLocal: ExecutionEnvironment) {
        if (environmentLocal.runProfile is MavenRunConfiguration) {
          val mavenGoal = (environmentLocal.runProfile as MavenRunConfiguration).runnerParameters.goals[0]
          if (mavenGoal == goal) {
            executionFlag.set(true)
          }
        }
      }
    })
  }
}
