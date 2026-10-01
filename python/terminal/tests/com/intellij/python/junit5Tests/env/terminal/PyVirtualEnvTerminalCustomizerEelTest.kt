// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.terminal

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.where
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.testFramework.junit5.eel.params.api.WslTest
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.terminal.PyVirtualEnvTerminalCustomizer
import com.intellij.python.venv.createVenv
import com.intellij.python.venv.createVenvAdditionalData
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.SdkCreationAdvancedOpts
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptionsImpl
import org.jetbrains.plugins.terminal.startup.ShellExecCommandImpl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest
import org.opentest4j.TestAbortedException
import kotlin.time.Duration.Companion.minutes

/**
 * The terminal on an eel must get the activation script path inside the eel,
 * not the path on the IDE side (PY-89111).
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.MAC, OS.LINUX])
internal class PyVirtualEnvTerminalCustomizerEelTest {
  private val projectFixture = projectFixture()

  // An instance fixture, so the directory is created on the eel of the test
  private val tempDirFixture = tempPathFixture()
  private val moduleFixture = projectFixture.pyModuleFixture(tempDirFixture, addPathToSourceRoot = true)

  @WslTest("ubuntu", mandatory = false)
  @DockerTest(image = "python:3.13.4", mandatory = false)
  @EelSource
  @ParameterizedTest
  fun activationScriptPathIsEelPath(eelHolder: EelHolder): Unit = timeoutRunBlocking(5.minutes) {
    val eel = eelHolder.eel
    // The local eel may have no python. A remote eel must have it: the WSL and Docker images do
    val python = eel.exec.where("python3")?.asNioPath()
    if (python == null && eel.descriptor == LocalEelDescriptor) {
      throw TestAbortedException("No local python")
    }
    checkNotNull(python) { "No python3 on ${eel.descriptor}" }

    val workingDirectory = tempDirFixture.get()
    val venvDir = workingDirectory.resolve(".venv")
    val venvPython = createVenv(python, venvDir).getOrThrow()
    val sdk = createSdk(
      PathHolder.Eel(venvPython),
      createVenvAdditionalData(workingDirectory),
      advancedOpts = SdkCreationAdvancedOpts(persist = true),
    ).orThrow().getSdkAPI()
    try {
      moduleFixture.get().pythonSdk = sdk

      val (shell, _) = eel.exec.getShell()
      val execOptions = MutableShellExecOptionsImpl(
        _execCommand = ShellExecCommandImpl(listOf(shell.toString())),
        workingDirectory = workingDirectory.asEelPath(),
        mutableEnvs = mutableMapOf(),
        shellIntegrationAvailable = true,
        requester = PyVirtualEnvTerminalCustomizer::class.java,
      )
      withContext(Dispatchers.IO) {
        PyVirtualEnvTerminalCustomizer().customizeExecOptions(projectFixture.get(), execOptions)
      }

      val source = execOptions.envs["_INTELLIJ_FORCE_SET_JEDITERM_SOURCE"]
      Assertions.assertNotNull(source, "venv must be activated via JEDITERM_SOURCE, but envs are: ${execOptions.envs}")
      val venvDirOnEel = venvDir.asEelPath().toString()
      Assertions.assertTrue(source!!.startsWith(venvDirOnEel),
                            "Activation script must be inside $venvDirOnEel (the path inside the eel), but it is $source")
    }
    finally {
      edtWriteAction {
        ProjectJdkTable.getInstance().removeJdk(sdk)
      }
    }
  }
}
