// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.google.gson.Gson
import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.target.local.LocalTargetEnvironment
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ProjectRule
import com.jetbrains.getPythonBinaryPath
import com.jetbrains.getPythonVersion
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonScriptExecution
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.flavors.conda.*
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.*
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Ensures conda SDK could be created
 */
@RunWith(Parameterized::class)
internal class PyCondaSdkTest {
  private val condaRule: LocalCondaRule = LocalCondaRule()

  private val yamlRule: CondaYamlFileRule = CondaYamlFileRule(condaRule)

  private val projectRule = ProjectRule()
  private val tempDirRule = TemporaryFolder()

  @Rule
  @JvmField
  internal val chain = RuleChain.outerRule(projectRule).around(condaRule).around(yamlRule).around(tempDirRule)

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
    Logger.getInstance(PyCondaSdkTest::class.java).info("Legacy: $useLegacy")
  }

  private suspend fun createCondaEnv(): PyCondaEnv {
    val name = "condaForTests"
    PyCondaEnv.createEnv(condaRule.condaCommand, NewCondaEnvRequest.EmptyNamedEnv(LanguageLevel.PYTHON38, name)).getOrThrow().waitFor()
    return PyCondaEnv(PyCondaEnvIdentity.NamedEnv(name), condaRule.condaPathOnTarget)
  }

  /**
   * When we create fresh local SDK on Windows, it must be patched with env vars, see [fixCondaPathEnvIfNeeded]
   */
  @Test
  fun testLocalActivationFix(): Unit = runTest {

    val script = tempDirRule.newFile()
    script.writeText("""
  import os
  from json import JSONEncoder
  print(JSONEncoder().encode(dict(os.environ)))
    """.trimIndent())

    Assume.assumeTrue("Windows only", SystemInfoRt.isWindows)
    val condaEnvs = PyCondaEnv.getEnvs(condaRule.commandExecutor, condaRule.condaPathOnTarget).getOrThrow()
    val baseEnv = condaEnvs.first { (it.envIdentity as? PyCondaEnvIdentity.UnnamedEnv)?.isBase == true }
    val nonBaseEnv = condaEnvs.firstOrNull { it.envIdentity is PyCondaEnvIdentity.NamedEnv } ?: createCondaEnv()

    for (condaEnv in arrayOf(nonBaseEnv, baseEnv)) {
      val condaSdk = condaRule.condaCommand.createCondaSdkFromExistingEnv(condaEnv.envIdentity, emptyList(), projectRule.project)
      val request = LocalTargetEnvironmentRequest()
      val targetEnvironment = LocalTargetEnvironment(request)

      val commandLine = PythonScriptExecution().apply {
        pythonScriptPath = TargetEnvironmentFunction { script.path }
      }.buildTargetedCommandLine(targetEnvironment, condaSdk, emptyList())

      val sdkHomePath = Path.of(condaSdk.homePath!!)
      val execResult = targetEnvironment.createProcess(commandLine).getBareExecutionResult()
      Assert.assertTrue("Error: ${execResult.stdErr.decodeToString()}", execResult.stdErr.isEmpty())
      val envVarsAsJson = execResult.stdOut.decodeToString()

      val envVars = Gson().fromJson(envVarsAsJson, Map::class.java).map { it.key.toString() to it.value.toString() }.toMap()
      MatcherAssert.assertThat("Conda not activated?", envVars.keys, Matchers.hasItem("PATH"))
      MatcherAssert.assertThat("Conda not activated?", envVars.keys, Matchers.hasItem("CONDA_PREFIX"))
      val paths = envVars["PATH"]!!.split(File.pathSeparator).map { Path.of(it) }
      MatcherAssert.assertThat("No conda python in PATH", paths, Matchers.hasItem(sdkHomePath.parent))
      val binPath = Path.of(condaEnv.fullCondaPathOnTarget).parent.parent.resolve("Library").resolve("Bin")
      MatcherAssert.assertThat("No conda lib bin path in PATH", paths, Matchers.hasItem(binPath))
    }
  }

  @Test
  fun testExecuteCommandOnSdk(): Unit = runTest(timeout = 20.seconds) {
    val condaEnv = PyCondaEnv.getEnvs(condaRule.commandExecutor, condaRule.condaPathOnTarget).getOrThrow().first()
    val sdk = condaRule.condaCommand.createCondaSdkFromExistingEnv(condaEnv.envIdentity, emptyList(), projectRule.project)
    val request = LocalTargetEnvironmentRequest()

    repeat(10) { // To measure time to compare legacy and local
      val version = getPythonVersion(sdk, request)
      Assert.assertNotNull("No version returned", version)
    }
  }

  @Test
  fun createSdkByFile() = runTest(timeout = 120.seconds) {
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
    val env = PyCondaEnv.getEnvs(condaRule.commandExecutor, condaRule.condaPathOnTarget).getOrThrow().first()
    val sdk = condaRule.condaCommand.createCondaSdkFromExistingEnv(env.envIdentity, emptyList(), projectRule.project)
    Assert.assertEquals(sdk.getOrCreateAdditionalData().flavor, CondaEnvSdkFlavor.getInstance())
    Assert.assertTrue(env.toString(), getPythonVersion(sdk, LocalTargetEnvironmentRequest())?.isNotBlank() == true)

    Assert.assertTrue("Bad home path", Files.isExecutable(Path.of(sdk.homePath!!)))
    ensureHomePathCorrect(sdk)
    Assert.assertEquals("Wrong name", env.envIdentity.userReadableName, sdk.name)
  }

  private suspend fun ensureHomePathCorrect(sdk: Sdk) {
    val homePath = Path.of(sdk.homePath!!)
    Assert.assertEquals("Wrong home path", homePath, Path.of(sdk.getPythonBinaryPath(projectRule.project).getOrThrow()))
  }

}