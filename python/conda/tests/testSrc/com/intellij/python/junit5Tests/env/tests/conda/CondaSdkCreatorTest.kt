package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.python.sdk.backend.removeTestSdkFromOpenProjects

import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.community.junit5Tests.framework.conda.createCondaEnv
import com.intellij.python.junit5Tests.framework.winLockedFile.deleteCheckLocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.testFramework.junit5.fixture.projectFixture

@PyEnvTestCaseWithConda
class CondaSdkCreatorTest {
  private val projectFixture = projectFixture()

  @Test
  fun createSdk(@CondaEnv condaEnv: PyCondaEnv, @TempDir path: Path): Unit = timeoutRunBlocking(10.minutes) {
    val condaPath = path.resolve("foo_bar_buz")
    val env = createCondaEnv(condaEnv, condaPath)
    val sdk = env.createSdkFromThisEnv(projectFixture.get(), null, path).getOrThrow().getSdkAPI()
    try {
      PythonSdkFlavor.tryDetectFlavorByLocalPath(Path.of(sdk.homePath!!))
      deleteCheckLocking(path)
    }finally {
      removeTestSdkFromOpenProjects(sdk)
    }
  }
}