package com.intellij.python.junit5Tests.env.tests

import com.intellij.python.sdk.backend.removeTestSdkFromOpenProjects

import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelType.Docker
import com.intellij.platform.testFramework.junit5.eel.params.api.EelType.Local
import com.intellij.platform.testFramework.junit5.eel.params.api.EelType.Wsl
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import org.opentest4j.TestAbortedException
import java.nio.file.Path
import kotlin.io.path.exists
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlin.time.Duration.Companion.minutes

@ParameterizedClass
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS], useLegacyTargets = true)
@DockerTest(mandatory = false, image = "continuumio/miniconda3")
class CondaEnvTest(val eelHolder: EelHolder) {
  private val projectFixture = projectFixture()

  @Test
  fun `conda sdk name should not contain nulls`() = timeoutRunBlocking(10.minutes) {
    val target = when (val targetType = eelHolder.type) {
      Docker -> eelHolder.target
      Local, Wsl -> throw TestAbortedException("target $targetType is not supported")
    }
    val condaBin = EelPathUtils.getNioPath("/opt/conda/bin/conda", eelHolder.eel.descriptor)
    val condaDir = EelPathUtils.getNioPath("/opt/conda/", eelHolder.eel.descriptor)

    assert(condaBin.exists())

    val env = PyCondaEnv(
      envIdentity = PyCondaEnvIdentity.UnnamedEnv(condaDir, isBase = true),
      fullCondaPathOnTarget = condaBin.asEelPath().toString(),
    )

    val pythonInterpreter = env.createSdkFromThisEnv(
      project = projectFixture.get(),
      targetConfig = target,
      workingDirectory = Path.of(""),
    ).getOrThrow()
    try {
      assert(!pythonInterpreter.getSdkAPI().name.contains("null"))
    }
    finally {
      removeTestSdkFromOpenProjects(pythonInterpreter.getSdkAPI())
    }
  }
}
