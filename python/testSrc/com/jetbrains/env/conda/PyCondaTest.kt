// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.conda

import com.intellij.execution.processTools.getResultStdout
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.processTools.mapFlat
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.testFramework.ProjectRule
import com.jetbrains.getPythonVersion
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest.*
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@OptIn(ExperimentalCoroutinesApi::class)
internal class PyCondaTest {

  private val languageLevel = LanguageLevel.PYTHON310

  private val condaRule: LocalCondaRule = LocalCondaRule()

  private val yamlRule: CondaYamlFileRule = CondaYamlFileRule(condaRule, languageLevel)

  @Rule
  @JvmField
  internal val chain = RuleChain.outerRule(ProjectRule()).around(condaRule).around(yamlRule)

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
    Assert.assertTrue("No base conda found", baseFound);
  }

  private suspend fun getPythonVersion(condaEnv: PyCondaEnv): String {
    val req = LocalTargetEnvironmentRequest()
    val commandLine = TargetedCommandLineBuilder(req).also { condaEnv.addCondaToTargetBuilder(sdk = null, it) }
    commandLine.addParameter("python")
    return getPythonVersion(commandLine, CondaEnvSdkFlavor.getInstance(), req) ?: error("No version for $condaEnv")
  }
}