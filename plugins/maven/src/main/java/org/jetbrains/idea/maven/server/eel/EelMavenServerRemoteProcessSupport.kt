// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.eel

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CompositeParameterTargetedValue
import com.intellij.execution.configurations.ParameterTargetValuePart
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.util.wsl.connectRetrying
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.execute
import com.intellij.platform.eel.fs.pathSeparator
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.transferLocalContentToRemote
import com.intellij.platform.eel.provider.utils.fetchLoginShellEnvVariablesBlocking
import com.intellij.platform.eel.provider.utils.forwardLocalPort
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.server.AbstractMavenServerRemoteProcessSupport
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenServerCMDState
import org.jetbrains.idea.maven.utils.MavenUtil.parseMavenProperties
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.rmi.server.RMIClientSocketFactory
import java.rmi.server.RMISocketFactory
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

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

  @Service(Service.Level.PROJECT)
  private class CoroutineService(val coroutineScope: CoroutineScope)

  override fun getRunProfileState(target: Any, configuration: Any, executor: Executor): RunProfileState {
    return EelMavenCmdState(eel, myProject, myJdk, myOptions, myDistribution, myDebugPort)
  }

  override fun getRemoteHost(): String {
    return "127.0.0.1"
  }

  override fun getClientSocketFactory(): RMIClientSocketFactory {
    val delegate = RMISocketFactory.getSocketFactory() ?: RMISocketFactory.getDefaultSocketFactory()
    return RetryingSocketFactory(delegate)
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun publishPort(port: Int): Int {
    myProject.service<CoroutineService>().coroutineScope.launch {
      forwardLocalPort(eel.tunnels, port, EelTunnelsApi.HostAddress.Builder(port.toUShort()).hostname(remoteHost).build())
    }
    return port
  }

  override fun type(): String {
    return "EEL" // TODO: which type we should use here?
  }
}

/**
 * This factory will retry Socket creation.
 *
 * WSL mirrored network has a visible delay (hundreds of ms) for ports to become available on the host machine.
 * We use EEL to access the remote port, but because Maven Server knows nothing about port forwarding, the local port on the WSL side is
 * still a WSL port.
 * So we have to retry a couple of times.
 */
private class RetryingSocketFactory(private val delegate: RMISocketFactory) : RMISocketFactory() {

  @Throws(IOException::class)
  override fun createSocket(host: String, port: Int): Socket {
    return connectRetrying(30.seconds.inWholeMilliseconds) {
      delegate.createSocket(host, port)
    }
  }

  @Throws(IOException::class)
  override fun createServerSocket(port: Int): ServerSocket {
    return delegate.createServerSocket(port)
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

  @Service(Service.Level.PROJECT)
  private class CoroutineService(val coroutineScope: CoroutineScope)

  // TODO: dispose?
  private val scope by lazy {
    project.service<CoroutineService>().coroutineScope.childScope("scope for: $this")
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
      transferLocalContentToRemote(
        source = Path(it),
        target = EelPathUtils.TransferTarget.Temporary(eel.descriptor)
      ).asEelPath().toString()
    }.joinToString(eel.fs.pathSeparator))

    return eelParams
  }

  private fun appendTargetedList(sinkParameterList: ParametersList, source: List<CompositeParameterTargetedValue>) {
    for (item in source) {
      val localizedItem = buildString {
        for (part in item.parts) {
          when (part) {
            is ParameterTargetValuePart.Const -> append(part.localValue)
            is ParameterTargetValuePart.Path -> append(transferLocalContentToRemote(
              source = Path.of(part.localValue),
              target = EelPathUtils.TransferTarget.Temporary(eel.descriptor)
            ).asEelPath().toString())
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
      val exe = Path.of(cmd.exePath).asEelPath()
      val builder = eel.exec.execute(exe.toString())
        .args(cmd.parametersList.parameters)
        .env(cmd.environment)
        .workingDirectory(EelPath.parse(getWorkingDirectory(), eel.descriptor))

      builder.getOrThrow()
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