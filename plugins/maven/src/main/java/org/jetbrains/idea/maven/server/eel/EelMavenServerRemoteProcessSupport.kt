// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.eel

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.utils.fetchLoginShellEnvVariablesBlocking
import com.intellij.platform.ijent.tunnels.forwardLocalPort
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.server.AbstractMavenServerRemoteProcessSupport
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenServerCMDState
import org.jetbrains.idea.maven.utils.MavenUtil.parseMavenProperties
import java.util.concurrent.CompletableFuture
import kotlin.io.path.Path

private val logger = logger<EelMavenServerRemoteProcessSupport>()

// TODO: should LocalMavenServerRemoteProcessSupport be aware about eel instead?
class EelMavenServerRemoteProcessSupport(
  private val eel: EelApi,
  jdk: Sdk,
  vmOptions: String?,
  mavenDistribution: MavenDistribution,
  project: Project,
  debugPort: Int?,
) : AbstractMavenServerRemoteProcessSupport(jdk, vmOptions, mavenDistribution, project, debugPort) {
  override fun getRunProfileState(target: Any, configuration: Any, executor: Executor): RunProfileState {
    return EelMavenCmdState(eel, myProject, myJdk, myOptions, myDistribution, myDebugPort)
  }

  override fun getRemoteHost(): String {
    return "127.0.0.1"
  }

  override fun publishPort(port: Int): Int {
    val deferred = CompletableFuture<Unit>()
    GlobalScope.launch {
      forwardLocalPort(eel.tunnels, port, eel.tunnels.hostAddressBuilder(port.toUShort()).hostname(remoteHost).build())
      deferred.complete(Unit)
    }
    deferred.join()
    return port
  }

  override fun type(): String {
    return "EEL" // TODO: which type we should use here?
  }
}

private class EelMavenCmdState(
  private val eel: EelApi,
  private val project: Project,
  jdk: Sdk,
  vmOptions: String?,
  mavenDistribution: MavenDistribution,
  debugPort: Int?,
) : MavenServerCMDState(jdk, vmOptions, mavenDistribution, debugPort) {
  override fun getWorkingDirectory(): String {
    return eel.exec.fetchLoginShellEnvVariablesBlocking()["HOME"] ?: TODO()
  }

  override fun getMavenOpts(): Map<String, String> {
    return parseMavenProperties(eel.exec.fetchLoginShellEnvVariablesBlocking()["MAVEN_OPTS"])
  }

  override fun createJavaParameters(): SimpleJavaParameters {
    return toEelParameters(super.createJavaParameters())
  }

  private fun toEelParameters(parameters: SimpleJavaParameters): SimpleJavaParameters {
    val eelParams = SimpleJavaParameters()

    eelParams.mainClass = parameters.mainClass

    val sdk = ProjectRootManager.getInstance(project).projectSdk

    eelParams.jdk = sdk

    if (logger.isTraceEnabled) {
      parameters.vmParametersList.defineProperty("sun.rmi.transport.logLevel", "VERBOSE")
      parameters.vmParametersList.defineProperty("sun.rmi.transport.tcp.logLevel", "VERBOSE")
      parameters.vmParametersList.defineProperty("sun.rmi.transport.proxy.logLevel", "VERBOSE")
      parameters.vmParametersList.defineProperty("sun.rmi.server.logLevel", "VERBOSE")
      parameters.vmParametersList.defineProperty("sun.rmi.client.logLevel", "VERBOSE")
      parameters.vmParametersList.defineProperty("java.rmi.server.logCalls", "true")
      parameters.vmParametersList.defineProperty("java.rmi.client.logCalls", "true")
    }

    for (item in parameters.programParametersList.parameters) {
      eelParams.programParametersList.add(item)
    }
    eelParams.charset = parameters.charset
    eelParams.vmParametersList.add("-classpath")
    eelParams.vmParametersList.add(parameters.classPath.pathList.mapNotNull { eel.mapper.getOriginalPath(Path(it))?.toString() }.joinToString(":"))

    return eelParams
  }

  override fun startProcess(): ProcessHandler {
    val params = createJavaParameters()
    val cmd = params.toCommandLine()

    val eelProcess = runBlockingMaybeCancellable {
      /**
       * Params normalization should be performed automatically
       * @see [com.intellij.execution.eel.EelApiWithPathsNormalization]
       */
      val builder = EelExecApi.executeProcessBuilder(cmd.exePath)
        .args(cmd.parametersList.parameters)
        .env(cmd.environment)
        .workingDirectory(workingDirectory)

      eel.exec.execute(builder).getOrThrow()
    }

    return object : KillableColoredProcessHandler(eelProcess.convertToJavaProcess(), cmd) {
      override fun killProcess() {
        runBlockingMaybeCancellable {
          eelProcess.kill()
        }
      }
    }
  }
}