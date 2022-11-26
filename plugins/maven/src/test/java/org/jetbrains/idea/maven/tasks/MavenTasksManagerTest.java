// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.maven.testFramework.MavenCompilingTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MavenTasksManagerTest extends MavenCompilingTestCase {
  @Test
  public void testRunExecuteBeforeBuildTasks() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    var mavenGoalExecuted = new AtomicBoolean(false);
    subscribeToMavenGoalExecution("clean", mavenGoalExecuted);

    addCompileTask(myProjectPom.getPath(), "clean");

    compileModules("project");

    assertTrue(mavenGoalExecuted.get());
  }

  @Test
  public void testRunExecuteBeforeBuildTasksInTheSameModule() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """);
    var m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """);
    var m2File = createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """);
    importProject();

    var mavenGoalExecuted = new AtomicBoolean(false);
    subscribeToMavenGoalExecution("generate-sources", mavenGoalExecuted);

    addCompileTask(m1File.getPath(), "generate-sources");

    compileModules("m1");

    assertTrue(mavenGoalExecuted.get());
  }

  @Test
  public void testDontRunExecuteBeforeBuildTasksInAnotherModule() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """);
    var m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """);
    var m2File = createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """);
    importProject();

    var mavenGoalExecuted = new AtomicBoolean(false);
    subscribeToMavenGoalExecution("generate-sources", mavenGoalExecuted);

    addCompileTask(m1File.getPath(), "generate-sources");

    compileModules("m2");

    assertFalse(mavenGoalExecuted.get());
  }

  private void addCompileTask(String pomPath, String goal) {
    var mavenTasksManager = MavenTasksManager.getInstance(myProject);
    var task = new MavenCompilerTask(pomPath, goal);
    mavenTasksManager.addCompileTasks(List.of(task), MavenTasksManager.Phase.BEFORE_COMPILE);
  }

  private void subscribeToMavenGoalExecution(String goal, AtomicBoolean executionFlag) {
    var connection = myProject.getMessageBus().connect();
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStartScheduled(@NotNull final String executorIdLocal, @NotNull final ExecutionEnvironment environmentLocal) {
        if (environmentLocal.getRunProfile() instanceof MavenRunConfiguration) {
          var mavenGoal = ((MavenRunConfiguration)environmentLocal.getRunProfile()).getRunnerParameters().getGoals().get(0);
          if (mavenGoal.equals(goal)) {
            executionFlag.set(true);
          }
        }
      }
    });
  }
}
