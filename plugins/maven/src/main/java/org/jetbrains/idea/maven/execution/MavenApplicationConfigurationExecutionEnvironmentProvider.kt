// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RemoteConnectionCreator
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.util.ExecutionErrorDialog
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.task.ExecuteRunConfigurationTask
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.maven.execution.MavenExecutionEnvironmentProviderUtil.patchVmParameters
import org.jetbrains.idea.maven.execution.build.MavenExecutionEnvironmentProvider
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.File

class MavenApplicationConfigurationExecutionEnvironmentProvider : MavenExecutionEnvironmentProvider {
  override fun isApplicable(task: ExecuteRunConfigurationTask): Boolean {
    return task.getRunProfile() is ApplicationConfiguration
  }

  override fun createExecutionEnvironment(
    project: Project, task: ExecuteRunConfigurationTask,
    executor: Executor?,
  ): ExecutionEnvironment? {
    var executor = executor
    val applicationConfiguration = task.getRunProfile() as ApplicationConfiguration
    val configurationFactory: ConfigurationFactory = MavenExecConfigurationFactory(applicationConfiguration)
    val mainClassName = applicationConfiguration.mainClassName
    if (StringUtil.isEmpty(mainClassName)) {
      return null
    }

    val module = applicationConfiguration.configurationModule.module
    if (module == null) {
      return null
    }

    val mavenProject = MavenProjectsManager.getInstance(project).findProject(module)
    if (mavenProject == null) {
      return null
    }

    //todo: Should be merged with MavenRunConfiguration
    val runnerAndConfigurationSettings =
      getInstance(project).createConfiguration(applicationConfiguration.name, configurationFactory)

    val mavenRunConfiguration = runnerAndConfigurationSettings.getConfiguration() as MyExecRunConfiguration

    mavenRunConfiguration.setBeforeRunTasks(applicationConfiguration.beforeRunTasks)
    copyLogParameters(applicationConfiguration, mavenRunConfiguration)

    val runnerParameters = mavenRunConfiguration.runnerParameters
    runnerParameters.workingDirPath = mavenProject.directory
    runnerParameters.pomFileName = mavenProject.file.getName()

    val javaParameters = JavaParameters()
    JavaParametersUtil.configureConfiguration(javaParameters, applicationConfiguration)

    val execArgs = ParametersList()
    execArgs.addAll(javaParameters.vmParametersList.getList())
    execArgs.add("-classpath")
    execArgs.add("%classpath")
    execArgs.add(mainClassName)
    execArgs.addParametersString(applicationConfiguration.programParameters)

    val execExecutable: String? = getJdkExecPath(applicationConfiguration)
    if (execExecutable == null) {
      throw RuntimeException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"))
    }

    val workingDirectory = ProgramParametersUtil.getWorkingDir(applicationConfiguration, project, module)

    val goals = ArrayList(runnerParameters.goals)
    if (StringUtil.isNotEmpty(workingDirectory)) {
      goals.add("-Dexec.workingdir=$workingDirectory")
    }
    goals.add("-Dexec.args=" + execArgs.parametersString)
    goals.add("-Dexec.executable=" + FileUtil.toSystemDependentName(execExecutable))
    goals.add("exec:exec")
    runnerParameters.setGoals(goals)

    if (executor == null) {
      executor = DefaultRunExecutor.getRunExecutorInstance()
    }

    return ExecutionEnvironmentBuilder(project, executor)
      .runProfile(mavenRunConfiguration)
      .runnerAndSettings(
        ProgramRunner.getRunner(executor.getId(), runnerAndConfigurationSettings.getConfiguration())!!,
        runnerAndConfigurationSettings
      )
      .build()
  }

  class MyExecRunConfiguration internal constructor(
    project: Project?, configurationFactory: ConfigurationFactory?,
    private val myApplicationConfiguration: ApplicationConfiguration,
  ) : MavenRunConfiguration(project, configurationFactory, myApplicationConfiguration.name) {
    override fun createRemoteConnectionCreator(javaParameters: JavaParameters): RemoteConnectionCreator {
      return object : RemoteConnectionCreator {
        override fun createRemoteConnection(environment: ExecutionEnvironment): RemoteConnection? {
          try {
            val parameters = JavaParameters()
            parameters.jdk = JavaParametersUtil.createProjectJdk(project, myApplicationConfiguration.alternativeJrePath)
            val connection = RemoteConnectionBuilder(false, DebuggerSettings.getInstance().getTransport(), "")
              .asyncAgent(true)
              .project(environment.project)
              .create(parameters)

            val programParametersList = javaParameters.programParametersList

            val execArgsPrefix = "-Dexec.args="
            val execArgsIndex = ContainerUtil.indexOf<String?>(
              programParametersList.getList(),
              Condition { s: String? -> s!!.startsWith(execArgsPrefix) })
            val execArgsStr = programParametersList.get(execArgsIndex)

            val execArgs = ParametersList()
            execArgs.addAll(patchVmParameters(parameters.vmParametersList))

            execArgs.addParametersString(execArgsStr.substring(execArgsPrefix.length))

            val classPath = FileUtil.toSystemDependentName(parameters.classPath.pathsString)
            execArgs.replaceOrPrepend("%classpath", "%classpath" + File.pathSeparator + classPath)

            programParametersList.set(execArgsIndex, execArgsPrefix + execArgs.parametersString)
            return connection
          }
          catch (e: ExecutionException) {
            throw RuntimeException("Cannot create debug connection", e)
          }
        }

        override fun isPollConnection(): Boolean {
          return true
        }
      }
    }
  }

  private class MavenExecConfigurationFactory(private val myApplicationConfiguration: ApplicationConfiguration) :
    ConfigurationFactory(MavenRunConfigurationType.getInstance()) {
    override fun getId(): String {
      return "Maven"
    }

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
      return MyExecRunConfiguration(project, this, myApplicationConfiguration)
    }

    override fun createConfiguration(name: String?, template: RunConfiguration): RunConfiguration {
      return MyExecRunConfiguration(template.getProject(), this, myApplicationConfiguration)
    }
  }

}

object MavenExecutionEnvironmentProviderUtil {
  fun patchVmParameters(vmParameters: ParametersList): List<String> {
    val patchedVmParameters: MutableList<String> = ArrayList(vmParameters.getList())
    val iterator: MutableIterator<String> = patchedVmParameters.iterator()
    while (iterator.hasNext()) {
      val parameter = iterator.next()
      if (parameter.contains("suspend=n,server=y")) {
        iterator.remove()
        patchedVmParameters.add(StringUtil.replace(parameter, "suspend=n,server=y", "suspend=y,server=y"))
        break
      }
    }
    return patchedVmParameters
  }
}

private fun getJdkExecPath(applicationConfiguration: ApplicationConfiguration): String? {
  val project = applicationConfiguration.project
  try {
    val jdk = JavaParametersUtil.createProjectJdk(project, applicationConfiguration.alternativeJrePath)
    if (jdk == null) {
      throw RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
    }

    val type = jdk.sdkType
    if (type !is JavaSdkType) {
      throw RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
    }

    return (type as JavaSdkType).getVMExecutablePath(jdk)
  }
  catch (e: CantRunException) {
    ExecutionErrorDialog.show(e, RunnerBundle.message("dialog.title.cannot.use.specified.jre"), project)
  }
  return null
}

private fun copyLogParameters(applicationConfiguration: ApplicationConfiguration, mavenRunConfiguration: MavenRunConfiguration) {
  for (file in applicationConfiguration.predefinedLogFiles) {
    mavenRunConfiguration.addPredefinedLogFile(file)
  }

  for (op in applicationConfiguration.logFiles) {
    mavenRunConfiguration.addLogFile(op.pathPattern, op.name, op.isEnabled, op.isSkipContent, op.isShowAll)
  }

  mavenRunConfiguration.setFileOutputPath(applicationConfiguration.outputFilePath)
  mavenRunConfiguration.isSaveOutputToFile = applicationConfiguration.isSaveOutputToFile

  mavenRunConfiguration.isShowConsoleOnStdOut = applicationConfiguration.isShowConsoleOnStdOut
  mavenRunConfiguration.isShowConsoleOnStdErr = applicationConfiguration.isShowConsoleOnStdErr
}