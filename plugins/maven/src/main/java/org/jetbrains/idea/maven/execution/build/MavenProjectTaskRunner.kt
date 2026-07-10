// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.build

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RunConfigurationModule
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.scratch.JavaScratchConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.artifacts.ArtifactProperties
import com.intellij.task.ExecuteRunConfigurationTask
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ModuleFilesBuildTask
import com.intellij.task.ModuleResourcesBuildTask
import com.intellij.task.ProjectModelBuildTask
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskNotification
import com.intellij.task.ProjectTaskResult
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.impl.JpsProjectTaskRunner
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.build.MavenProjectTaskRunnerUtil.runBatch
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil.isMavenModule

class MavenProjectTaskRunner : ProjectTaskRunner() {
  override fun run(project: Project, context: ProjectTaskContext, vararg tasks: ProjectTask): Promise<Result> {
    val promise = AsyncPromise<Result>()
    val callback: ProjectTaskNotification = ProjectTaskNotificationAdapter(promise)
    val taskMap = JpsProjectTaskRunner.groupBy(listOf(*tasks))

    buildModuleFiles(project, callback, getFromGroupedMap(taskMap, ModuleFilesBuildTask::class.java))
    buildModules(project, callback, getFromGroupedMap(taskMap, ModuleResourcesBuildTask::class.java))
    buildModules(project, callback, getFromGroupedMap(taskMap, ModuleBuildTask::class.java))

    buildArtifacts(project, context, callback, getFromGroupedMap(taskMap, ProjectModelBuildTask::class.java))
    return promise
  }

  override fun canRun(project: Project, projectTask: ProjectTask, context: ProjectTaskContext?): Boolean {
    if (context != null && context.runConfiguration is JavaScratchConfiguration) {
      return false
    }

    if (!MavenRunner.getInstance(project).settings.isDelegateBuildToMaven) {
      return false
    }

    if (projectTask is ModuleBuildTask) {
      return isMavenModule(projectTask.getModule())
    }

    if (projectTask is ProjectModelBuildTask<*>) {
      val artifact = projectTask.getBuildableElement()
      if (artifact is Artifact) {
        var properties: MavenArtifactProperties? = null
        for (provider in artifact.getPropertiesProviders()) {
          if (provider is MavenArtifactPropertiesProvider) {
            val artifactProperties: ArtifactProperties<*>? = artifact.getProperties(provider)
            if (artifactProperties is MavenArtifactProperties) {
              properties = artifactProperties as MavenArtifactProperties
              break
            }
          }
        }
        if (properties?.getModuleName() == null) {
          return false
        }

        val module = getInstance(project).findModuleByName(properties.getModuleName())
        if (!isMavenModule(module)) {
          return false
        }

        for (artifactBuilder in MavenArtifactBuilder.EP_NAME.extensions) {
          if (artifactBuilder.isApplicable(projectTask)) {
            return true
          }
        }
      }
    }

    if (projectTask is ExecuteRunConfigurationTask) {
      val runProfile = projectTask.getRunProfile()
      if (runProfile is JavaScratchConfiguration) {
        return false
      }
      if (runProfile is ModuleBasedConfiguration<*, *>) {
        val module: RunConfigurationModule = runProfile.getConfigurationModule()
        if (!isMavenModule(module.module)) {
          return false
        }
        for (environmentProvider in MavenExecutionEnvironmentProvider.EP_NAME.extensions) {
          if (environmentProvider.isApplicable(projectTask)) {
            return true
          }
        }
      }
    }

    return false
  }

  override fun createExecutionEnvironment(
    project: Project,
    task: ExecuteRunConfigurationTask,
    executor: Executor?,
  ): ExecutionEnvironment? {
    for (environmentProvider in MavenExecutionEnvironmentProvider.EP_NAME.extensions) {
      if (environmentProvider.isApplicable(task)) {
        return environmentProvider.createExecutionEnvironment(project, task, executor)
      }
    }
    return null
  }

  private class ProjectTaskNotificationAdapter(private val myPromise: AsyncPromise<in Result>) : ProjectTaskNotification {
    override fun finished(taskResult: ProjectTaskResult) {
      myPromise.setResult(object : Result {
        override fun isAborted(): Boolean {
          return taskResult.isAborted
        }

        override fun hasErrors(): Boolean {
          return taskResult.errors > 0
        }
      })
    }
  }

}

private fun <T : ProjectTask> getFromGroupedMap(
  map: MutableMap<Class<out ProjectTask>, List<ProjectTask>>,
  key: Class<T>,
): List<T> {
  val result = map[key] ?: return emptyList()
  return result.filterIsInstance(key)
}

private fun buildModules(
  project: Project,
  callback: ProjectTaskNotification?,
  moduleBuildTasks: Collection<ModuleBuildTask>,
) {
  if (moduleBuildTasks.isEmpty()) return

  val mavenProjectsManager = MavenProjectsManager.getInstance(project)

  val explicitProfiles = mavenProjectsManager.explicitProfiles
  val rootProjectsToModules: MutableMap<MavenProject, MutableList<MavenProject>> = HashMap()

  var buildOnlyResources = false
  for (moduleBuildTask in moduleBuildTasks) {
    val mavenProject = mavenProjectsManager.findProject(moduleBuildTask.getModule())
    if (mavenProject == null) continue

    buildOnlyResources = buildOnlyResources || moduleBuildTask is ModuleResourcesBuildTask
    val rootProject = mavenProjectsManager.findRootProject(mavenProject)
    rootProjectsToModules.computeIfAbsent(rootProject) { mutableListOf(mavenProject) }
  }

  val clean = moduleBuildTasks.any { it !is ModuleFilesBuildTask && !it.isIncrementalBuild() }
  val compileOnly = moduleBuildTasks.all { it is ModuleFilesBuildTask }
  val includeDependentModules = moduleBuildTasks.any { it.isIncludeDependentModules() }
  val goal: String = getGoal(buildOnlyResources, compileOnly)
  val commands: MutableList<MavenRunnerParameters> = ArrayList()
  for ((key, mavenProjects) in rootProjectsToModules) {
    val parameters = ParametersList()
    if (clean) {
      parameters.add("clean")
    }
    parameters.add(goal)

    if (!includeDependentModules) {
      if (mavenProjects.size > 1) {
        parameters.add("--projects")
        parameters.add(
          mavenProjects.joinToString(",") {
            val id = it.mavenId
            "${id.groupId}:${id.artifactId}"
          }
        )
      }
      else {
        parameters.add("--non-recursive")
      }
    }

    val pomFile = (if (mavenProjects.size > 1) key else mavenProjects[0]).file
    commands.add(
      MavenRunnerParameters(
        true,
        pomFile.getParent().getPath(),
        pomFile.getName(),
        parameters.getList(),
        explicitProfiles.enabledProfiles,
        explicitProfiles.disabledProfiles
      )
    )
  }

  runBatch(project, commands, callback)
}

private fun getGoal(buildOnlyResources: Boolean, compileOnly: Boolean): String {
  if (buildOnlyResources) {
    return "resources:resources"
  }
  return if (compileOnly) "compile" else "install"
}

object MavenProjectTaskRunnerUtil {
  @JvmStatic
   fun runBatch(
    project: Project,
    commands: MutableList<MavenRunnerParameters>,
    callback: ProjectTaskNotification?,
  ) {
    ApplicationManager.getApplication().invokeAndWait(Runnable {
      FileDocumentManager.getInstance().saveAllDocuments()
      for (command in commands) {
        MavenRunConfigurationType.runConfiguration(
          project,
          command,
          null,
          null,
          ProgramRunner.Callback { descriptor ->
            if (callback == null) {
              return@Callback
            }
            val handler = descriptor.processHandler
            handler?.addProcessListener(object : ProcessListener {
              override fun processTerminated(event: ProcessEvent) {
                if (event.exitCode == 0) {
                  callback.finished(ProjectTaskResult(false, 0, 0))
                }
                else {
                  callback.finished(ProjectTaskResult(true, 0, 0))
                }
              }
            })
          },
          true
        )
      }
    })
  }
}

private fun buildModuleFiles(
  project: Project,
  callback: ProjectTaskNotification?,
  moduleFilesBuildTasks: Collection<ModuleFilesBuildTask>,
) {
  buildModules(project, callback, moduleFilesBuildTasks)
}

private fun buildArtifacts(
  project: Project,
  context: ProjectTaskContext,
  callback: ProjectTaskNotification?,
  tasks: List<ProjectModelBuildTask<*>>,
) {
  for (buildTask in tasks) {
    if (buildTask.getBuildableElement() is Artifact) {
      for (artifactBuilder in MavenArtifactBuilder.EP_NAME.extensions) {
        if (artifactBuilder.isApplicable(buildTask)) {
          artifactBuilder.build(project, buildTask, context, callback)
        }
      }
    }
  }
}