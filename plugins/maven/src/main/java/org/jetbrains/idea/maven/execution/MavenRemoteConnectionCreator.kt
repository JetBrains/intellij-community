// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import org.jetbrains.idea.maven.execution.run.MavenRemoteConnectionWrapper

abstract class MavenRemoteConnectionCreator {
  abstract fun createRemoteConnection(javaParameters: JavaParameters, runConfiguration: MavenRunConfiguration): RemoteConnection?
  abstract fun createRemoteConnectionForScript(runConfiguration: MavenRunConfiguration): MavenRemoteConnectionWrapper?
  protected fun createConnection(project: Project, parameters: JavaParameters): RemoteConnection {
    try {
      // there's no easy and reliable way to know the version of target JRE, but without it there won't be any debugger agent settings
      parameters.setJdk(JavaParametersUtil.createProjectJdk(project, null))
      return RemoteConnectionBuilder(false, DebuggerSettings.getInstance().transport, "")
        // IDEA-375478 -- this is a workaround to prevent Maven from failing.
        // The proper solution is to copy the java async agent Jar to a remote machine and
        // make it possible to execute Maven with the agent on the remote target.
        // The solution is already in the master branch, but it leads to a major change on the debugger side, so, for 232
        // we are going with a simple workaround just by not attaching the agent Jar at all.
        .asyncAgent(Registry.`is`("maven.use.scripts.debug.agent") && project.getEelDescriptor() == LocalEelDescriptor)
        .project(project)
        .create(parameters)
    }
    catch (e: ExecutionException) {
      throw RuntimeException("Cannot create debug connection", e)
    }
  }
}