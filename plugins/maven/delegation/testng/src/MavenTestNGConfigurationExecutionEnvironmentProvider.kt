// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.delegation.testng

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.Executor
import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.project.Project
import com.intellij.task.ExecuteRunConfigurationTask
import com.theoryinpractice.testng.configuration.TestNGConfiguration
import com.theoryinpractice.testng.model.TestType
import org.jetbrains.idea.maven.execution.MavenSurefireConfigurationFactory
import org.jetbrains.idea.maven.execution.MavenSurefireRunConfiguration
import org.jetbrains.idea.maven.execution.build.MavenExecutionEnvironmentProvider
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenTestNGConfigurationExecutionEnvironmentProvider : MavenExecutionEnvironmentProvider {

  override fun isApplicable(task: ExecuteRunConfigurationTask): Boolean = task.runProfile is TestNGConfiguration

  override fun isTestConfiguration(): Boolean = true

  override fun createExecutionEnvironment(
    project: Project,
    task: ExecuteRunConfigurationTask,
    executor: Executor?,
  ): ExecutionEnvironment? {
    val testNgConfiguration = task.runProfile as TestNGConfiguration
    val module = testNgConfiguration.configurationModule.module ?: return null
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val leafProject = mavenProjectsManager.findProject(module) ?: return null
    val rootProject = mavenProjectsManager.findRootProject(leafProject)
    val testParam = buildTestParam(testNgConfiguration) ?: return null

    val configurationFactory = MavenSurefireConfigurationFactory(testNgConfiguration.name, testNgConfiguration.alternativeJrePath)
    val runnerAndConfigurationSettings = getInstance(project).createConfiguration(testNgConfiguration.name, configurationFactory)
    val mavenRunConfiguration = runnerAndConfigurationSettings.configuration as MavenSurefireRunConfiguration
    mavenRunConfiguration.beforeRunTasks = emptyList<BeforeRunTask<*>>()

    val runnerParameters = mavenRunConfiguration.runnerParameters
    // Run from root reactor so upstream modules are built fresh.
    runnerParameters.workingDirPath = rootProject.directory
    runnerParameters.pomFileName = rootProject.file.name
    // Run from root reactor with --also-make so upstream modules are built fresh;
    // scope execution to the leaf module so only its tests run.
    val mavenId = leafProject.mavenId
    val projectSpec = if (mavenId.groupId != null) "${mavenId.groupId}:${mavenId.artifactId}" else ":${mavenId.artifactId}"
    runnerParameters.setGoals(ArrayList(runnerParameters.goals).apply {
      add("--projects=$projectSpec")
      add("--also-make")
      add("-Dtest=$testParam")
      add("-DfailIfNoTests=false")
      add("-Dsurefire.failIfNoSpecifiedTests=false")
      add("test")
    })

    val resolvedExecutor = executor ?: DefaultRunExecutor.getRunExecutorInstance()
    return ExecutionEnvironmentBuilder(project, resolvedExecutor)
      .runProfile(mavenRunConfiguration)
      .runnerAndSettings(
        ProgramRunner.getRunner(resolvedExecutor.id, runnerAndConfigurationSettings.configuration)!!,
        runnerAndConfigurationSettings,
      )
      .build()
  }

  private fun buildTestParam(configuration: TestNGConfiguration): String? {
    val data = configuration.persistantData
    return when (data.TEST_OBJECT) {
      TestType.CLASS.type -> data.mainClassName.ifEmpty { null }

      TestType.METHOD.type -> {
        val className = data.mainClassName.ifEmpty { return null }
        val methodName = data.methodName.ifEmpty { return null }
        "$className#$methodName"
      }

      TestType.PACKAGE.type -> {
        val pkg = data.packageName
        if (pkg.isEmpty()) "*" else "$pkg.*"
      }

      TestType.PATTERN.type -> {
        val patterns = data.patterns
        if (patterns.isEmpty()) return null
        patterns.joinToString("+")
      }

      else -> null
    }
  }
}
