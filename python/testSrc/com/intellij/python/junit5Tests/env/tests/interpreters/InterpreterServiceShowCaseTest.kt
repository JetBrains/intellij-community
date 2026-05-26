// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.python.community.interpreters.InterpreterService
import com.intellij.python.community.interpreters.InvalidInterpreter
import com.intellij.python.community.interpreters.ValidInterpreter
import com.intellij.python.community.interpreters.executeGetStdout
import com.intellij.python.community.interpreters.executeHelper
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToPath
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@PyEnvTestCase
class InterpreterServiceShowCaseTest {
  private val sdkFixture = pySdkFixture()
  private val validSdkFixture by sdkFixture.pyVenvFixture(tempPathFixture(prefix = "valid"), addToSdkTable = true, moduleFixture = null)
  private val validSdkFixture2 by sdkFixture.pyVenvFixture(tempPathFixture(prefix = "valid2"), addToSdkTable = true, moduleFixture = null)

  private val invalidSdkFixture by sdkFixture.pyVenvFixture(tempPathFixture(prefix = "invalid"), addToSdkTable = true, moduleFixture = null)
  private val sdkFixtureAnotherPath by sdkFixture.pyVenvFixture(tempPathFixture(prefix = "anotherpath"), addToSdkTable = true, moduleFixture = null)

  private val moduleFixture = projectFixture().moduleFixture()


  @Test
  fun testListInterpreters(@TempDir dir: Path): Unit = runBlocking {
    val interpreterService = InterpreterService()

    edtWriteAction {
      val m = invalidSdkFixture.sdkModificator
      m.homePath += "junk"
      m.commitChanges()
    }

    val validSdk = validSdkFixture
    validSdk.setAssociationToPath(null)
    validSdkFixture2.setAssociationToPath(dir.pathString)
    sdkFixtureAnotherPath.setAssociationToPath(dir.resolveSibling("asdasd").pathString)

    val module = moduleFixture.get()
    val expectedInterpreters = setOf(sdkFixture.get().sdk, validSdkFixture, validSdkFixture2,
                                     invalidSdkFixture).associateBy { it.id() }.toMutableMap()
    val interpreters = interpreterService.getInterpreters(dir)
    for (i in interpreters) {
      val sdk = expectedInterpreters.remove(i.id)
      Assertions.assertTrue(sdk != null, "Unexpected interpreter: $i")
      Assertions.assertEquals(sdk, i.sdk, "Wrong sdk")

      module.pythonSdk = sdk!!
      Assertions.assertEquals(sdk.pySdkAdditionalData.uuid, interpreterService.getForModule(module)!!.id, "No module set")

      when (i) {
        is InvalidInterpreter -> {
          Assertions.assertEquals(invalidSdkFixture.id(), i.id, "Unexpected invalid interpreter $i")
        }
        is ValidInterpreter -> {
          val version = ExecService().executeGetStdout(i, listOf("--version")).orThrow().trim()
          Assertions.assertTrue(version.isNotBlank(), "No version returned")

          val helperName = "file.py"
          val helper = PythonHelpersLocator.getCommunityHelpersRoot().resolve(helperName)
          try {
            helper.writeText("print(1)")
            val helperOutput = ExecService().executeHelper(i, helperName).orThrow().trim()
            Assertions.assertEquals("1", helperOutput, "Wrong helper output")
          }
          finally {
            helper.deleteIfExists()
          }
        }
      }
    }
    if (expectedInterpreters.isNotEmpty()) {
      fail("Missing interpreters: ${expectedInterpreters.values}")
    }
  }
}

private fun Sdk.id(): UUID = pySdkAdditionalData.uuid