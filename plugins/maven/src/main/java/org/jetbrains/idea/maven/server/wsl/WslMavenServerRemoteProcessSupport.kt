// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.wsl

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.idea.maven.server.AbstractMavenServerRemoteProcessSupport
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.WslMavenDistribution

class WslMavenServerRemoteProcessSupport(private val myWslDistribution: WSLDistribution,
                                         jdk: Sdk,
                                         vmOptions: String?,
                                         mavenDistribution: WslMavenDistribution,
                                         project: Project,
                                         debugPort: Int?) : AbstractMavenServerRemoteProcessSupport(jdk, vmOptions, mavenDistribution,
                                                                                                    project, debugPort) {
  override fun getRunProfileState(target: Any, configuration: Any, executor: Executor): RunProfileState {
    return WslMavenCmdState(myWslDistribution, myJdk, myOptions, myDistribution as WslMavenDistribution, myDebugPort, myProject, remoteHost)
  }

  override fun getRemoteHost(): String = myWslDistribution.wslIp

  override fun type() = "WSL"
}


