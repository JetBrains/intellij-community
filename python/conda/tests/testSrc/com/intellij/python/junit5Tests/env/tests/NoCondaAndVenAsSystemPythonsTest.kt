package com.intellij.python.junit5Tests.env.tests

import com.intellij.python.sdk.backend.removeTestSdkFromOpenProjects

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.mockPathAndAdd
import com.intellij.python.junit5Tests.framework.unMockPath
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.flavors.WinPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.pySdkAdditionalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.cartesian.CartesianTest
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import com.intellij.testFramework.junit5.fixture.projectFixture


@EnabledOnOs(OS.WINDOWS)
@ExtendWith(SystemStubsExtension::class)
@PyEnvTestCaseWithConda
internal class NoCondaAndVenAsSystemPythonsTest {

  companion object {
    private val tempFixture = tempPathFixture()
    private val projectFixture = projectFixture()
    private val pyProjectFixture = projectFixture.pyProjectFixture()
    private val venvSdkFixture = pyEnvironmentFixture().pyVenvFixture(tempFixture, pyProjectFixture)
    private lateinit var ourSystemPython: PythonBinary
    private lateinit var condaSdk: Sdk
    private lateinit var pythonsToExclude: List<PythonBinary>

    @SystemStub
    @JvmStatic
    private val environment = EnvironmentVariables()


    @BeforeAll
    @JvmStatic
    fun setUp(@CondaEnv condaEnv: PyCondaEnv, @PythonBinaryPath condaBasePython: PythonBinary): Unit = timeoutRunBlocking(5.minutes) {
      ourSystemPython = condaBasePython
      condaSdk = condaEnv.createSdkFromThisEnv(projectFixture.get(), null, venvSdkFixture.get().getSdkAPI().pySdkAdditionalData.workingDirectory).getOrThrow().getSdkAPI()
      pythonsToExclude = sdkHomePaths(condaSdk, venvSdkFixture.get().getSdkAPI()) + listOf(condaBasePython)
      val newBinPaths = pythonsToExclude.map { it.parent }.toTypedArray()
      environment.mockPathAndAdd(*newBinPaths)
    }

    @JvmStatic
    @AfterAll
    fun tearDown(): Unit = timeoutRunBlocking {
      removeTestSdkFromOpenProjects(condaSdk)
      environment.unMockPath()
    }

    private fun sdkHomePaths(vararg sdk: Sdk): List<Path> =
      sdk.map { Path.of(it.homePath!!) }
  }

  enum class Api { WIN_SDK_FLAVOR, SYSTEM_PYTHON_SERVICE }

  /**
   * We add conda, venv and system python to the env. Path. We check that only system python is reported.
   */
  @CartesianTest
  fun testNoCondaNoVenv(@CartesianTest.Enum howToGetPythons: Api): Unit = timeoutRunBlocking(10.minutes) {
    withContext(Dispatchers.IO) {
      WinPythonSdkFlavor.getInstance().dropCaches()
      val suggestedPaths = when (howToGetPythons) {
        Api.WIN_SDK_FLAVOR -> WinPythonSdkFlavor.getInstance().suggestLocalHomePaths(null, null)
        Api.SYSTEM_PYTHON_SERVICE -> SystemPythonService().findSystemPythons(localEel, forceRefresh = true).map { it.pythonBinary }
      }
      assertThat("Python of incorrect flavor returned",
                 suggestedPaths,
                 not(hasItems(*pythonsToExclude.toTypedArray())))
    }
  }
}