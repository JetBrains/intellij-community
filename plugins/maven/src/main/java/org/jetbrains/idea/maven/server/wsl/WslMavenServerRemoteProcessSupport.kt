// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.wsl

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.externalSystem.util.wsl.connectRetrying
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.idea.maven.server.AbstractMavenServerRemoteProcessSupport
import org.jetbrains.idea.maven.server.WslMavenDistribution
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.rmi.server.RMIClientSocketFactory
import java.rmi.server.RMISocketFactory

internal class WslMavenServerRemoteProcessSupport(private val myWslDistribution: WSLDistribution,
                                                  jdk: Sdk,
                                                  vmOptions: String?,
                                                  mavenDistribution: WslMavenDistribution,
                                                  project: Project,
                                                  debugPort: Int?) : AbstractMavenServerRemoteProcessSupport(jdk, vmOptions,
                                                                                                             mavenDistribution,
                                                                                                             project, debugPort) {

  override fun getRunProfileState(target: Any, configuration: Any, executor: Executor): RunProfileState {
    return WslMavenCmdState(myWslDistribution, myJdk, myOptions, myDistribution as WslMavenDistribution, myDebugPort, myProject, remoteHost)
  }

  override fun getRemoteHost(): String = myWslDistribution.wslIpAddress.hostAddress

  override fun getClientSocketFactory(): RMIClientSocketFactory {
    val delegate = RMISocketFactory.getSocketFactory() ?: RMISocketFactory.getDefaultSocketFactory()
    return RetryingSocketFactory(delegate)
  }

  override fun type() = "WSL"
}

/**
 * This factory will retry sockets creation.
 *
 * WSL mirrored network has a visible delay (hundreds of ms) in ports becoming available on host machine. So we have to retry a couple of times.
 */
class RetryingSocketFactory(val delegate: RMISocketFactory) : RMISocketFactory() {
  @Throws(IOException::class)
  override fun createSocket(host: String, port: Int): Socket {
    return connectRetrying(3000) { delegate.createSocket(host, port) }
  }

  @Throws(IOException::class)
  override fun createServerSocket(port: Int): ServerSocket {
    return delegate.createServerSocket(port)
  }
}