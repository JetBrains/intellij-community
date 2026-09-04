// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.delegation.junit

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.Executor
import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.project.Project
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenSurefireConfigurationFactory
import org.jetbrains.idea.maven.execution.MavenSurefireRunConfiguration
import org.jetbrains.idea.maven.execution.build.MavenExecutionEnvironmentProvider
import org.jetbrains.idea.maven.model.MavenSource
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.nio.file.Path

class MavenJUnitConfigurationExecutionEnvironmentProvider : MavenExecutionEnvironmentProvider {

  override fun isApplicable(task: ExecuteRunConfigurationTask): Boolean {
    return task.runProfile is JUnitConfiguration
  }

  override fun isTestConfiguration(): Boolean = true

  override fun createExecutionEnvironment(
    project: Project,
    task: ExecuteRunConfigurationTask,
    executor: Executor?,
  ): ExecutionEnvironment? {
    val junitConfig = task.runProfile as JUnitConfiguration
    val module = junitConfig.configurationModule.module ?: return null
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val leafProject = mavenProjectsManager.findProject(module) ?: return null
    val rootProject = mavenProjectsManager.findRootProject(leafProject)

    val testParam = buildTestParam(junitConfig, leafProject) ?: return null

    val configurationFactory = MavenSurefireConfigurationFactory(junitConfig.name, junitConfig.alternativeJrePath)
    val runnerAndConfigurationSettings =
      getInstance(project).createConfiguration(junitConfig.name, configurationFactory)

    val mavenRunConfiguration = runnerAndConfigurationSettings.configuration as MavenSurefireRunConfiguration
    mavenRunConfiguration.beforeRunTasks = emptyList<BeforeRunTask<*>>()
    mavenRunConfiguration.testModuleDirectory = leafProject.directory
    mavenRunConfiguration.runnerSettings = MavenRunner.getInstance(project).state.clone().apply { isSkipTests = false }
    val reportSuffix = System.currentTimeMillis().toString()
    mavenRunConfiguration.reportSuffix = reportSuffix

    val runnerParameters = mavenRunConfiguration.runnerParameters
    // Run from root reactor so upstream modules are built fresh.
    runnerParameters.workingDirPath = rootProject.directory
    runnerParameters.pomFileName = rootProject.file.name

    val goals = ArrayList(runnerParameters.goals)
    // Run from root reactor with --also-make so upstream modules are built fresh;
    // scope execution to the leaf module so only its tests run.
    val mavenId = leafProject.mavenId
    if (mavenId.groupId == null) {
      // groupId is absent for non-standard POMs; leading colon selects by artifactId alone.
      goals.add("--projects=:${mavenId.artifactId}")
    } else {
      goals.add("--projects=${mavenId.groupId}:${mavenId.artifactId}")
    }
    goals.add("--also-make")
    goals.add("-Dtest=$testParam")
    goals.add("-DfailIfNoTests=false")
    goals.add("-Dsurefire.failIfNoSpecifiedTests=false")
    goals.add("-Dsurefire.reportNameSuffix=$reportSuffix")
    goals.add("test")
    runnerParameters.setGoals(goals)

    val resolvedExecutor = executor ?: DefaultRunExecutor.getRunExecutorInstance()
    val runner = ProgramRunner.getRunner(resolvedExecutor.id, runnerAndConfigurationSettings.configuration) ?: return null
    return ExecutionEnvironmentBuilder(project, resolvedExecutor)
      .runProfile(mavenRunConfiguration)
      .runnerAndSettings(runner, runnerAndConfigurationSettings)
      .build()
  }
}

/**
 * Converts a [JUnitConfiguration] to a Surefire `-Dtest=` parameter string.
 * Returns null for test types that cannot be expressed as a Surefire filter
 * (e.g. unique ID, tags) — in that case the IDE runner will be used instead.
 */
private fun buildTestParam(config: JUnitConfiguration, leafProject: MavenProject): String? {
  val data = config.persistentData
  return when (data.TEST_OBJECT) {
    JUnitConfiguration.TEST_CLASS -> data.mainClassName.ifEmpty { null }

    JUnitConfiguration.TEST_METHOD -> {
      val className = data.mainClassName.ifEmpty { return null }
      val methodName = data.methodName.ifEmpty { return null }
      "$className#$methodName"
    }

    JUnitConfiguration.TEST_PACKAGE -> {
      val pkg = data.packageName
      if (pkg.isEmpty()) "*" else "$pkg.*"
    }

    JUnitConfiguration.TEST_PATTERN -> {
      val patterns = data.patterns
      if (patterns.isEmpty()) return null
      // Surefire uses + to separate multiple test specs
      patterns.joinToString("+") { patternToSurefireSpec(it) }
    }

    JUnitConfiguration.TEST_DIRECTORY -> {
      val dirName = data.dirName.ifEmpty { return null }
      packageFromDirectory(dirName, leafProject) ?: return null
    }

    // TEST_UNIQUE_ID, TEST_TAGS, BY_SOURCE_POSITION, BY_SOURCE_CHANGES
    // are not expressible as simple Surefire -Dtest= filters; fall back to IDE runner
    else -> null
  }
}

/**
 * Converts a JUnit pattern entry (e.g. "com.example.MyTest" or "com.example.MyTest,method")
 * to a Surefire test spec.
 */
@ApiStatus.Internal
fun patternToSurefireSpec(pattern: String): String {
  // JUnit patterns may contain "ClassName,methodName" with comma separator
  val commaIdx = pattern.indexOf(',')
  return if (commaIdx >= 0) {
    val cls = pattern.substring(0, commaIdx)
    val method = pattern.substring(commaIdx + 1)
    "$cls#$method"
  }
  else {
    pattern
  }
}

/**
 * Derives a Surefire `-Dtest=` pattern for running all tests under [dirPath].
 *
 * Resolves [dirPath] relative to the Maven project's test source roots; returns
 * `"<package>.*"` for a subdirectory or `"*"` if the directory is the root itself.
 * Returns null when [dirPath] is not under any known test source root (fall back to IDE runner).
 */
private fun packageFromDirectory(dirPath: String, leafProject: MavenProject): String? {
  val dir = Path.of(dirPath).normalize()
  for (src in leafProject.mavenSources) {
    if (!MavenSource.isTestSource(src)) continue
    val root = Path.of(src.directory).normalize()
    if (dir == root) return "*"
    if (dir.startsWith(root)) {
      val pkg = root.relativize(dir).toString().replace('/', '.').replace('\\', '.')
      return "$pkg.*"
    }
  }
  return null
}
