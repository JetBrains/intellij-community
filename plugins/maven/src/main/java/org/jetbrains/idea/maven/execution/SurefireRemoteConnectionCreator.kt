// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import org.jetbrains.idea.maven.execution.run.MavenRemoteConnectionWrapper

/**
 * Handles debug connection setup for Maven Surefire test runs in script mode (`maven.use.scripts=true`).
 *
 * Surefire receives the JDWP arguments through `-Dmaven.surefire.debug`, so the debugger connects to the forked test
 * JVM rather than the Maven process itself.
 */
internal class SurefireRemoteConnectionCreator : MavenRemoteConnectionCreator() {

  override fun createRemoteConnectionForScript(runConfiguration: MavenRunConfiguration): MavenRemoteConnectionWrapper? {
    if (runConfiguration !is SurefireRunConfiguration) return null

    val parameters = JavaParameters()
    val connection = createConnection(runConfiguration.project, parameters)
    val jdwpArgs = MavenExecutionEnvironmentProviderUtil.patchVmParameters(parameters.vmParametersList)
      .joinToString(" ")

    if (jdwpArgs.isNotEmpty()) {
      val goals = ArrayList(runConfiguration.runnerParameters.goals)
      // Replace any stale -Dmaven.surefire.debug= left by a previous debug run (idempotent on rerun).
      val idx = goals.indexOfFirst { it.startsWith("-Dmaven.surefire.debug=") }
      if (idx >= 0) goals[idx] = "-Dmaven.surefire.debug=$jdwpArgs" else goals.add("-Dmaven.surefire.debug=$jdwpArgs")
      runConfiguration.runnerParameters.setGoals(goals)
    }

    return MavenRemoteConnectionWrapper(connection) { mavenOpts -> mavenOpts }
  }

  // In script mode the connection is built via createRemoteConnectionForScript(); this path is not reached for Surefire configs.
  override fun createRemoteConnection(javaParameters: JavaParameters, runConfiguration: MavenRunConfiguration): RemoteConnection? = null
}
