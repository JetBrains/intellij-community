// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.conda

import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.ProjectRule
import com.jetbrains.getPythonVersion
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.flavors.conda.*
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.getPythonBinaryPath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Ensures conda SDK could be created
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PyCondaSdkTest {
  @JvmField
  @Rule
  val condaRule: LocalCondaRule = LocalCondaRule()

  @JvmField
  @Rule
  val projectRule: ProjectRule = ProjectRule()


  @Test
  fun createSdkByFile() = runTest {
    val newCondaInfo = NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(yamlFile)
    val sdk = condaRule.condaCommand.createCondaSdkAlongWithNewEnv(newCondaInfo, coroutineContext, emptyList(),
                                                                   projectRule.project).getOrThrow()
    val env = (sdk.getOrCreateAdditionalData().flavorAndData.data as PyCondaFlavorData).env
    val namedEnv = env.envIdentity as PyCondaEnvIdentity.NamedEnv
    Assert.assertEquals("Wrong env name", yamlEnvName, namedEnv.envName)
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
  }

  private suspend fun ensureHomePathCorrect(sdk: Sdk) {
    val homePath = sdk.homePath!!
    Assert.assertEquals("Wrong home path", homePath, sdk.getPythonBinaryPath(projectRule.project).getOrThrow())
  }

}