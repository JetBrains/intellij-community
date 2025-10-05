// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.getPythonVersion
import com.jetbrains.python.conda.loadLocalPythonCondaPath
import com.jetbrains.python.conda.saveLocalPythonCondaPath
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.flavors.conda.*
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest.EmptyNamedEnv
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile
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
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
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
  fun testBasePython(): Unit =  timeoutRunBlocking(10.minutes) { 
    val baseConda = PyCondaEnv.getEnvs(condaRule.condaPathOnTarget).getOrThrow()
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
  fun testLocalPathSaveLoad() {
    saveLocalPythonCondaPath(condaRule.condaPath)
    Assert.assertEquals("Incorrectly loaded path", condaRule.condaPath, loadLocalPythonCondaPath())
  }

  @Test
  fun testCondaCreateByYaml() =  timeoutRunBlocking(60.seconds) {
    PyCondaEnv.createEnv(condaRule.condaCommand,
                         LocalEnvByLocalEnvironmentFile(yamlRule.yamlFilePath, emptyList())).getOrThrow()
    val condaEnv = PyCondaEnv.getEnvs(condaRule.condaPathOnTarget)
      .getOrThrow().first { (it.envIdentity as? PyCondaEnvIdentity.NamedEnv)?.envName == yamlRule.envName }

    // Python version contains word "Python", LanguageLevel doesn't expect it
    val pythonVersion = getPythonVersion(condaEnv).trimStart { !it.isDigit() && it != '.' }
    Assert.assertEquals("Wrong python version installed", languageLevel, LanguageLevel.fromPythonVersion(pythonVersion))
  }

  @Test
  fun testCondaCreateEnv(): Unit =  timeoutRunBlocking(20.seconds) {
    val envName = "myNewEnvForTests"
    PyCondaEnv.createEnv(condaRule.condaCommand,
                         EmptyNamedEnv(LanguageLevel.PYTHON39, envName)).getOrThrow()
    PyCondaEnv.getEnvs(condaRule.condaPathOnTarget)
      .getOrThrow().first { (it.envIdentity as? PyCondaEnvIdentity.NamedEnv)?.envName == envName }
  }

  @Test
  fun testCondaListEnvs(): Unit =  timeoutRunBlocking(10.minutes) { 
    val condaEnvs = PyCondaEnv.getEnvs(condaRule.condaPathOnTarget).getOrThrow()
    Assert.assertTrue("No environments returned", condaEnvs.isNotEmpty())

    var baseFound = false

    //Check first three, checking same for all envs may be too slow
    condaEnvs.take(3).forEach { condaEnv ->
      val version = getPythonVersion(condaEnv)
      Assert.assertTrue(condaEnv.envIdentity.toString(), version.isNotBlank())
      println("${condaEnv.envIdentity}: $version")
    }

    for (condaEnv in condaEnvs) {
      if ((condaEnv.envIdentity as? PyCondaEnvIdentity.UnnamedEnv)?.isBase == true) {
        Assert.assertFalse("More than one base environment", baseFound)
        baseFound = true
      }
    }
    Assert.assertTrue("No base conda found", baseFound)
  }

  @Test
  fun testCondaListUnnamedEnvs(): Unit =  timeoutRunBlocking(90.seconds) {
    val envDirs = CondaExecutor.listEnvs(Path.of(condaRule.condaPathOnTarget)).mapSuccess { it.envsDirs }.getOrThrow()
    val envsDirs = Path.of(envDirs.first())
    val childDir = envsDirs.resolve("child")
    val childEnvPrefix = childDir.resolve("childEnv").toString()
    val siblingDir = envsDirs.resolveSibling("${envsDirs.fileName}Sibling")
    val siblingEnvPrefix = siblingDir.resolve("siblingEnv").toString()

    PyCondaEnv.createEnv(condaRule.condaCommand,
                         NewCondaEnvRequest.EmptyUnnamedEnv(LanguageLevel.PYTHON39, childEnvPrefix)).getOrThrow()
    PyCondaEnv.createEnv(condaRule.condaCommand,
                         NewCondaEnvRequest.EmptyUnnamedEnv(LanguageLevel.PYTHON39, siblingEnvPrefix)).getOrThrow()

    // Important to check that envIdentity is UnnamedEnv as this is testing to make sure that
    // getEnvs doesn't mistakenly return a NamedEnv for an environment that isn't a direct child of envsDirs
    val envs = PyCondaEnv.getEnvs(condaRule.condaPathOnTarget).getOrThrow()
      .map { it.envIdentity }
      .filterIsInstance<PyCondaEnvIdentity.UnnamedEnv>()
    Assert.assertTrue("No child $childEnvPrefix in $envs", envs.any { it.envPath == childEnvPrefix })
    Assert.assertTrue("No sibling $siblingEnvPrefix in $envs", envs.any { it.envPath == siblingEnvPrefix })
  }

  private suspend fun getPythonVersion(condaEnv: PyCondaEnv): String {
    val req = LocalTargetEnvironmentRequest()
    val commandLine = TargetedCommandLineBuilder(req).also { condaEnv.addCondaToTargetBuilder(it) }
    commandLine.addParameter("python")
    return getPythonVersion(commandLine, CondaEnvSdkFlavor.getInstance(), req) ?: error("No version for $condaEnv")
  }
}