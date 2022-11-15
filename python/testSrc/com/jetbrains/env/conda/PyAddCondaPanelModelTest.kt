// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.conda

import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressSink
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ProjectRule
import com.intellij.util.io.exists
import com.jetbrains.getPythonVersion
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.target.conda.PyAddCondaPanelModel
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path


@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PyAddCondaPanelModelTest {

  @JvmField
  @Rule
  val condaRule: LocalCondaRule = LocalCondaRule()

  @JvmField
  @Rule
  val projectRule: ProjectRule = ProjectRule()


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


  @Test
  fun testCondaDetection(): Unit = runTest {
    val model = PyAddCondaPanelModel(null, emptyList(), projectRule.project)
    model.detectConda(coroutineContext)
    val detectedPath = model.condaPathTextBoxRwProp.get()
    if (detectedPath.isNotEmpty()) {
      Assert.assertEquals("Wrong path detected", condaRule.condaPathOnTarget, detectedPath)
    }
  }

  @Test
  fun testCondaCreateNewEnv() {
    val condaName = "someNewCondaEnv"
    runBlocking {
      val model = PyAddCondaPanelModel(null, emptyList(), projectRule.project)
      model.condaPathTextBoxRwProp.set(condaRule.condaPath.toString())
      model.condaActionCreateNewEnvRadioRwProp.set(true)
      model.condaActionUseExistingEnvRadioRwProp.set(false)
      model.newEnvLanguageLevelRwProperty.set(LanguageLevel.PYTHON38)
      Assert.assertNotNull("Empty conda env name didn't lead to validation", model.getValidationError())
      model.newEnvNameRwProperty.set("d     f --- ")
      Assert.assertNotNull("Bad conda name didn't lead to validation", model.getValidationError())
      model.newEnvNameRwProperty.set(condaName)


      val mockSink = MockSink()
      val sdk = model.onCondaCreateSdkClicked(coroutineContext, mockSink).getOrThrow()
      val newName = ((sdk.getOrCreateAdditionalData().flavorAndData.data as PyCondaFlavorData).env.envIdentity as PyCondaEnvIdentity.NamedEnv).envName
      Assert.assertEquals("Wrong conda name", condaName, newName)
      Assert.assertTrue("No output provided for sink", mockSink.out.toString().isNotEmpty())
    }
  }

  @Test
  fun testCondaUseExistingEnv(): Unit = runTest {
    val model = PyAddCondaPanelModel(null, emptyList(), projectRule.project)
    model.condaPathTextBoxRwProp.set(condaRule.condaPath.toString())
    model.onLoadEnvsClicked(coroutineContext)
    model.condaActionUseExistingEnvRadioRwProp.set(true)
    model.condaActionCreateNewEnvRadioRwProp.set(false)
    model.condaEnvModel.selectedItem = model.condaEnvModel.getElementAt(0)
    val sdk = model.onCondaCreateSdkClicked(coroutineContext, null).getOrThrow()
    Assert.assertTrue(getPythonVersion(sdk, LocalTargetEnvironmentRequest())!!.isNotBlank())
    Assert.assertTrue(Path.of(sdk.homePath!!).exists())
  }

  @Test
  fun testCondaModelValidation(): Unit = runTest {
    val model = PyAddCondaPanelModel(null, emptyList(), projectRule.project)
    Assert.assertNotNull("No validation error, even though path not set", model.getValidationError())

    Assert.assertFalse(model.showCondaPathSetOkButtonRoProp.get())
    model.condaPathTextBoxRwProp.set("Some random path that doesn't exist")
    Assert.assertTrue(model.showCondaPathSetOkButtonRoProp.get())
    model.onLoadEnvsClicked(coroutineContext)
    Assert.assertNotNull("No validation error, but path is incorrect", model.getValidationError())

    model.condaPathTextBoxRwProp.set(condaRule.condaPath.toString())
    model.onLoadEnvsClicked(coroutineContext)
    Assert.assertNull("Unexpected validation error", model.getValidationError())

    Assert.assertTrue(model.showCondaActionsPanelRoProp.get())
    model.condaActionCreateNewEnvRadioRwProp.set(true)
    Assert.assertTrue("No conda envs loaded", model.condaEnvModel.size > 0)

    model.condaActionCreateNewEnvRadioRwProp.set(true)
    model.condaActionUseExistingEnvRadioRwProp.set(false)
    Assert.assertEquals("Env name not set", projectRule.project.name, model.newEnvNameRwProperty.get())

    model.newEnvNameRwProperty.set("")
    Assert.assertNotNull("No validation error, but conda env name not set", model.getValidationError())

    model.newEnvNameRwProperty.set("SomeEnvName")
    Assert.assertNull("Unexpected error", model.getValidationError())
  }

  /**
   * Mock doesn't work with Kotlin, hence mock manually
   */
  private class MockSink : ProgressSink {
    val out = StringBuilder()
    override fun update(text: String?, details: String?, fraction: Double?) {
      text?.let { out.append(it) }
    }
  }
}