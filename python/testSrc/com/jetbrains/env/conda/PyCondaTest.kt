// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.conda

import com.intellij.execution.processTools.getResultStdout
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.processTools.mapFlat
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ProjectRule
import com.intellij.util.io.exists
import com.jetbrains.getPythonVersion
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest.*
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.addCondaPythonToTargetCommandLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.nio.file.Path

@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class)
internal class PyCondaTest {

  private val languageLevel = LanguageLevel.PYTHON310

  private val condaRule: LocalCondaRule = LocalCondaRule()

  private val yamlRule: CondaYamlFileRule = CondaYamlFileRule(condaRule, languageLevel)

  @Rule
  @JvmField
  internal val chain = RuleChain.outerRule(ProjectRule()).around(condaRule).around(yamlRule)

  @Parameter(0)
  @JvmField
  var useLegacy: Boolean = false

  companion object {
    @JvmStatic
    @Parameters
    fun data(): Collection<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
  }

  @Before
  fun before() {
    Registry.get("use.python.for.local.conda").setValue(useLegacy)
    Logger.getInstance(PyCondaSdkTest::class.java).info("Legacy: $useLegacy")
  }

  @Test
  fun testBasePython(): Unit = runTest {
    val baseConda = PyCondaEnv.getEnvs(condaRule.condaCommand).getOrThrow()
      .first { (it.envIdentity as? PyCondaEnvIdentity.UnnamedEnv)?.isBase == true }
    val targetRequest = LocalTargetEnvironmentRequest()
    val commandLineBuilder = TargetedCommandLineBuilder(targetRequest)
    addCondaPythonToTargetCommandLine(commandLineBuilder, baseConda, null)
    commandLineBuilder.addParameter("-c")
    commandLineBuilder.addParameter("import conda; print(conda.CONDA_PACKAGE_ROOT)")
    val targetEnv = targetRequest.prepareEnvironment(TargetProgressIndicator.EMPTY)
    val condaPackageRoot = targetEnv.createProcess(commandLineBuilder.build())
      .getResultStdoutStr()
      .getOrThrow()
    Assert.assertTrue("Script should return path to conda packages", Path.of(condaPackageRoot).exists())
  }

  @Test
  fun testCondaCreateByYaml() = runTest {
    PyCondaEnv.createEnv(condaRule.condaCommand,
                         LocalEnvByLocalEnvironmentFile(yamlRule.yamlFilePath)).mapFlat { it.getResultStdoutStr() }.getOrThrow()
    val condaEnv = PyCondaEnv.getEnvs(
      condaRule.condaCommand).getOrThrow().first { (it.envIdentity as? PyCondaEnvIdentity.NamedEnv)?.envName == yamlRule.envName }

    // Python version contains word "Python", LanguageLevel doesn't expect it
    val pythonVersion = getPythonVersion(condaEnv).trimStart { !it.isDigit() && it != '.' }
    Assert.assertEquals("Wrong python version installed", languageLevel, LanguageLevel.fromPythonVersion(pythonVersion))
  }

  @Test
  fun testCondaCreateEnv(): Unit = runTest {
    val envName = "myNewEnvForTests"
    PyCondaEnv.createEnv(condaRule.condaCommand,
                         EmptyNamedEnv(LanguageLevel.PYTHON39, envName)).mapFlat { it.getResultStdout() }
    PyCondaEnv.getEnvs(condaRule.condaCommand).getOrThrow().first { (it.envIdentity as? PyCondaEnvIdentity.NamedEnv)?.envName == envName }
  }

  @Test
  fun testCondaListEnvs(): Unit = runTest {
    val condaEnvs = PyCondaEnv.getEnvs(condaRule.condaCommand).getOrThrow()
    Assert.assertTrue("No environments returned", condaEnvs.isNotEmpty())

    var baseFound = false
    for (condaEnv in condaEnvs) {
      val version = getPythonVersion(condaEnv)
      Assert.assertTrue(condaEnv.envIdentity.toString(), version.isNotBlank())
      println("${condaEnv.envIdentity}: $version")
      if ((condaEnv.envIdentity as? PyCondaEnvIdentity.UnnamedEnv)?.isBase == true) {
        Assert.assertFalse("More than one base environment", baseFound)
        baseFound = true
      }
    }
    Assert.assertTrue("No base conda found", baseFound);
  }

  private suspend fun getPythonVersion(condaEnv: PyCondaEnv): String {
    val req = LocalTargetEnvironmentRequest()
    val commandLine = TargetedCommandLineBuilder(req).also { condaEnv.addCondaToTargetBuilder(it) }
    commandLine.addParameter("python")
    return getPythonVersion(commandLine, CondaEnvSdkFlavor.getInstance(), req) ?: error("No version for $condaEnv")
  }
}