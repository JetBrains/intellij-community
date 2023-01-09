// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.execution.Platform
import com.intellij.execution.target.*
import com.intellij.execution.target.value.constant
import com.intellij.openapi.progress.ProgressIndicator
import org.assertj.core.api.SoftAssertions
import org.junit.Test

class PydevConsoleRunnerUtilTest {
  @Test
  fun `test constructPyPathAndWorkingDirCommand`() {
    val dummyRequest = DummyTargetEnvironmentRequest()
    val dummyEnvironment = DummyTargetEnvironment(dummyRequest)

    val pythonPath = mutableListOf(constant("/home/foo/bar's baz"))
    val workingDir = constant("/home/foo/bar\\qux")

    val consoleStartCommand = PydevConsoleRunnerImpl.CONSOLE_START_COMMAND

    SoftAssertions.assertSoftly { softly ->
      softly
        .assertThat(constructPyPathAndWorkingDirCommand(pythonPath, workingDir, consoleStartCommand).apply(dummyEnvironment))
        .isEqualTo(
          """|import sys; print('Python %s on %s' % (sys.version, sys.platform))
             |sys.path.extend(['/home/foo/bar\'s baz', '/home/foo/bar\\qux'])
             |""".trimMargin()
        )
        .describedAs("Constructs the command that adds the python paths and working directory to `sys.path`")
    }
  }

  private class DummyTargetEnvironmentRequest : TargetEnvironmentRequest {
    override val targetPlatform: TargetPlatform = TargetPlatform(Platform.UNIX)

    override val configuration: TargetEnvironmentConfiguration? = null

    override var projectPathOnTarget: String = ""

    @Deprecated("Use uploadVolumes")
    override val defaultVolume: TargetEnvironmentRequest.Volume
      get() = throw UnsupportedOperationException()

    override fun prepareEnvironment(progressIndicator: TargetProgressIndicator): TargetEnvironment = DummyTargetEnvironment(this)

    override fun onEnvironmentPrepared(callback: (environment: TargetEnvironment, progressIndicator: TargetProgressIndicator) -> Unit) =
      throw UnsupportedOperationException()
  }

  private class DummyTargetEnvironment(request: DummyTargetEnvironmentRequest) : TargetEnvironment(request) {
    override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process =
      throw UnsupportedOperationException()

    override val targetPlatform: TargetPlatform = request.targetPlatform

    override fun shutdown() = Unit
  }
}