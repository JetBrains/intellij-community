// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.conda

import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ProjectRule
import com.jetbrains.getPythonVersion
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import com.jetbrains.python.sdk.flavors.conda.*
import com.jetbrains.python.sdk.flavors.conda.CondaPathFix.Companion.shouldBeFixed
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.getPythonBinaryPath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.jdom.Element
import org.junit.*
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Ensures conda SDK could be created
 */
@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class)
internal class PyCondaSdkTest {
  private val condaRule: LocalCondaRule = LocalCondaRule()

  private val yamlRule: CondaYamlFileRule = CondaYamlFileRule(condaRule)

  private val projectRule = ProjectRule()

  @Rule
  @JvmField
  internal val chain = RuleChain.outerRule(projectRule).around(condaRule).around(yamlRule)

  @Parameterized.Parameter(0)
  @JvmField
  var useLegacy: Boolean = false

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Collection<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
  }

  @Before
  fun before() {
    Registry.get("use.python.for.local.conda").setValue(useLegacy)
  }

  private suspend fun createCondaEnv(): PyCondaEnv {
    val name = "condaForTests"
    PyCondaEnv.createEnv(condaRule.condaCommand, NewCondaEnvRequest.EmptyNamedEnv(LanguageLevel.PYTHON38, name)).getOrThrow().waitFor()
    return PyCondaEnv(PyCondaEnvIdentity.NamedEnv(name), condaRule.condaPathOnTarget)
  }
  /**
   * When we create fresh local SDK on Windows, it must be patched with env vars, see [CondaPathFix]
   */
  @Test
  fun testLocalActivationFix(): Unit = runTest {
    Assume.assumeTrue("Windows only", SystemInfoRt.isWindows)
    val env = PyCondaEnv.getEnvs(
      condaRule.condaCommand).getOrThrow().first { (it.envIdentity as? PyCondaEnvIdentity.UnnamedEnv)?.isBase == true }
    val condaSdk = condaRule.condaCommand.createCondaSdkFromExistingEnv(env.envIdentity, emptyList(), projectRule.project)
    val request = LocalTargetEnvironmentRequest()
    val builder = TargetedCommandLineBuilder(request)
    Assert.assertTrue("No conda path fix suggested for Windows?", builder.shouldBeFixed)
    condaSdk.configureBuilderToRunPythonOnTarget(builder)
    builder.apply {
      addParameter("-c")
      addParameter("import os; print(' '.join(list(os.environ.keys())))")
    }
    val envVars = builder.build().environmentVariables.map { it.key.uppercase(Locale.getDefault()) to it.value }.toMap()
    MatcherAssert.assertThat("Conda not activated?", envVars.keys, Matchers.hasItem("PATH"))
    MatcherAssert.assertThat("Conda not activated?", envVars.keys, Matchers.hasItem("CONDA_PREFIX"))
    val paths = envVars["PATH"]!!.split(File.pathSeparator).map { Path.of(it) }
    MatcherAssert.assertThat("No conda python in PATH", paths, Matchers.hasItem(Path.of(condaSdk.homePath!!).parent))
  }

  @Test
  fun testExecuteCommandOnSdk(): Unit = runTest {
    val condaEnv = PyCondaEnv.getEnvs(condaRule.condaCommand).getOrThrow().first()
    val sdk = condaRule.condaCommand.createCondaSdkFromExistingEnv(condaEnv.envIdentity, emptyList(), projectRule.project)
    val request = LocalTargetEnvironmentRequest()

    repeat(10) { // To measure time to compare legacy and local
      val version = getPythonVersion(sdk, request)
      Assert.assertNotNull("No version returned", version)
    }
  }

  @Test
  fun testConvertToConda() = runTest {
    System.setProperty("NO_FS_ROOTS_ACCESS_CHECK", "true")

    val env = PyCondaEnv.getEnvs(condaRule.condaCommand).getOrThrow().first()
    val condaSdk = condaRule.condaCommand.createCondaSdkFromExistingEnv(env.envIdentity, emptyList(), projectRule.project)
    val pythonPath = condaSdk.homePath

    val legacyPythonSdk = ProjectJdkImpl("my conda", PythonSdkType.getInstance()).apply {
      homePath = pythonPath
    }
    val element = Element("root")
    legacyPythonSdk.writeExternal(element)
    legacyPythonSdk.readExternal(element)
    val fixedAdditionalData = legacyPythonSdk.getOrCreateAdditionalData()
    Assert.assertEquals("Wrong flavor", CondaEnvSdkFlavor.getInstance(), fixedAdditionalData.flavor)
    Assert.assertEquals("Wrong env", env, (fixedAdditionalData.flavorAndData.data as PyCondaFlavorData).env)
  }

  @Test
  fun createSdkByFile() = runTest {
    val newCondaInfo = NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(yamlRule.yamlFilePath)
    val sdk = condaRule.condaCommand.createCondaSdkAlongWithNewEnv(newCondaInfo, coroutineContext, emptyList(),
                                                                   projectRule.project).getOrThrow()
    val env = (sdk.getOrCreateAdditionalData().flavorAndData.data as PyCondaFlavorData).env
    val namedEnv = env.envIdentity as PyCondaEnvIdentity.NamedEnv
    Assert.assertEquals("Wrong env name", yamlRule.envName, namedEnv.envName)
    ensureHomePathCorrect(sdk)
  }

  @Test
  fun testCreateFromExisting() = runTest {
    val env = PyCondaEnv.getEnvs(condaRule.condaCommand).getOrThrow().first()
    val sdk = condaRule.condaCommand.createCondaSdkFromExistingEnv(env.envIdentity, emptyList(), projectRule.project)
    Assert.assertEquals(sdk.getOrCreateAdditionalData().flavor, CondaEnvSdkFlavor.getInstance())
    Assert.assertTrue(env.toString(), getPythonVersion(sdk, LocalTargetEnvironmentRequest())?.isNotBlank() == true)

    Assert.assertTrue("Bad home path", Files.isExecutable(Path.of(sdk.homePath!!)))
    ensureHomePathCorrect(sdk)
    Assert.assertEquals("Wrong name", env.envIdentity.userReadableName, sdk.name)
  }

  private suspend fun ensureHomePathCorrect(sdk: Sdk) {
    val homePath = sdk.homePath!!
    Assert.assertEquals("Wrong home path", homePath, sdk.getPythonBinaryPath(projectRule.project).getOrThrow())
  }

}