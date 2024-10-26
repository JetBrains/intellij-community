// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.eel

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.pathSeparator
import com.intellij.platform.eel.provider.utils.fetchLoginShellEnvVariablesBlocking
import com.intellij.platform.ide.progress.ModalTaskOwner.project
import com.intellij.platform.ijent.tunnels.forwardLocalPort
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.MavenCoroutineScopeHolder
import org.jetbrains.idea.maven.server.AbstractMavenServerRemoteProcessSupport
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenServerCMDState
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenUtil.parseMavenProperties
import java.nio.file.Path
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

  @OptIn(DelicateCoroutinesApi::class)
  override fun publishPort(port: Int): Int {
    MavenCoroutineScopeProvider.getCoroutineScope(myProject).launch {
      forwardLocalPort(eel.tunnels, port, EelTunnelsApi.hostAddressBuilder(port.toUShort()).hostname(remoteHost).build())
    }
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
  // TODO: dispose?
  private val scope by lazy {
    MavenCoroutineScopeProvider.getCoroutineScope(project).childScope("scope for: $this")
  }

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

    appendTargetedList(eelParams.vmParametersList, parameters.vmParametersList.targetedList)

    appendTargetedList(eelParams.programParametersList, parameters.programParametersList.targetedList)

    eelParams.charset = parameters.charset
    eelParams.vmParametersList.add("-classpath")
    eelParams.vmParametersList.add(parameters.classPath.pathList.mapNotNull {
      runBlockingCancellable { eel.mapper.maybeUploadPath(Path(it), scope).toString() }
    }.joinToString(eel.fs.pathSeparator))

    return eelParams
  }

  private fun appendTargetedList(sinkParameterList: ParametersList, source: List<CompositeParameterTargetedValue>) {
    for (item in source) {
      val localizedItem = buildString {
        for (part in item.parts) {
          when (part) {
            is ParameterTargetValuePart.Const -> append(part.localValue)
            is ParameterTargetValuePart.Path -> runBlockingCancellable {
              append(eel.mapper.maybeUploadPath(Path.of(part.localValue), scope).toString())
            }
            ParameterTargetValuePart.PathSeparator -> append(eel.fs.pathSeparator)
            is ParameterTargetValuePart.PromiseValue -> append(part.localValue) // todo?
          }
        }
      }
      sinkParameterList.add(localizedItem)
    }
  }

  override fun startProcess(): ProcessHandler {
    val params = createJavaParameters()
    val cmd = params.toCommandLine()

    val eelProcess = runBlockingMaybeCancellable {
      /**
       * Params normalization should be performed automatically
       * @see [com.intellij.execution.eel.EelApiWithPathsNormalization]
       */
      val exe = eel.mapper.getOriginalPath(Path.of(cmd.exePath)) ?: error("Cannot find exe for ${cmd.exePath}")
      val builder = EelExecApi.executeProcessBuilder(exe.toString())
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