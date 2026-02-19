// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.MavenPropertyResolver
import org.jetbrains.idea.maven.execution.run.MavenRemoteConnectionWrapper
import org.jetbrains.idea.maven.model.MavenConstants
import java.io.File
import java.util.regex.Pattern

private val EXEC_MAVEN_PLUGIN_PATTERN = Pattern.compile("org[.]codehaus[.]mojo:exec-maven-plugin(:[\\d.]+)?:exec")

private fun getExecArgsFromPomXml(project: Project, runnerParameters: MavenRunnerParameters): String {
  val workingDir = VfsUtil.findFileByIoFile(runnerParameters.workingDirFile, false) ?: return ""
  val pomFileName = StringUtil.defaultIfEmpty(runnerParameters.pomFileName, MavenConstants.POM_XML)
  val pomFile = workingDir.findChild(pomFileName) ?: return ""
  val projectModel = MavenDomUtil.getMavenDomProjectModel(project, pomFile) ?: return ""
  return MavenPropertyResolver.resolve("\${exec.args}", projectModel) ?: return ""
}

private fun appendToClassPath(execArgs: ParametersList, classPath: String) {
  val execArgsList = execArgs.list
  var classPathIndex = execArgsList.indexOf("-classpath")
  if (classPathIndex == -1) {
    classPathIndex = execArgsList.indexOf("-cp")
  }
  if (classPathIndex == -1) {
    execArgs.prependAll("-classpath", "%classpath" + File.pathSeparator + classPath)
  }
  else if (classPathIndex + 1 == execArgsList.size) { // invalid command line, but we still have to patch it
    execArgs.add("%classpath" + File.pathSeparator + classPath)
  }
  else {
    val oldClassPath = execArgs[classPathIndex + 1]
    execArgs[classPathIndex + 1] = oldClassPath + File.pathSeparator + classPath
  }
}

internal class ExecRemoteConnectionCreator : MavenRemoteConnectionCreator() {

  override fun createRemoteConnectionForScript(runConfiguration: MavenRunConfiguration): MavenRemoteConnectionWrapper {
    val parameters = JavaParameters()
    val connection = createConnection(runConfiguration.project, parameters)
    val parametersOfConnection = parameters.vmParametersList

    return MavenRemoteConnectionWrapper(connection) { mavenOpts ->

      if (mavenOpts.isEmpty()) return@MavenRemoteConnectionWrapper parametersOfConnection.parametersString

      return@MavenRemoteConnectionWrapper "${parametersOfConnection.parametersString} $mavenOpts"
    }

  }

  override fun createRemoteConnection(javaParameters: JavaParameters, runConfiguration: MavenRunConfiguration): RemoteConnection? {
    val programParametersList = javaParameters.programParametersList
    if (programParametersList.list.find { it == "exec:exec" || EXEC_MAVEN_PLUGIN_PATTERN.matcher(it).matches() } == null) {
      return null
    }

    val project = runConfiguration.getProject()
    val parameters = JavaParameters()
    val connection = createConnection(project, parameters)

    val runnerParameters = runConfiguration.getRunnerParameters()
    val execArgsPrefix = "-Dexec.args="
    val execArgsIndex = programParametersList.list.indexOfFirst { it.startsWith(execArgsPrefix) }
    val execArgsStr =
      if (execArgsIndex != -1) {
        programParametersList[execArgsIndex].substring(execArgsPrefix.length)
      }
      else {
        getExecArgsFromPomXml(project, runnerParameters)
      }

    val execArgs = ParametersList()
    execArgs.addAll(MavenApplicationConfigurationExecutionEnvironmentProvider.patchVmParameters(parameters.vmParametersList))

    execArgs.addParametersString(execArgsStr)

    val classPath = FileUtil.toSystemDependentName(parameters.classPath.pathsString)
    if (classPath.isNotEmpty()) {
      appendToClassPath(execArgs, classPath)
    }

    val execArgsCommandLineArg = execArgsPrefix + execArgs.parametersString
    if (execArgsIndex != -1) {
      programParametersList[execArgsIndex] = execArgsCommandLineArg
    }
    else {
      programParametersList.add(execArgsCommandLineArg)
    }

    return connection
  }
}