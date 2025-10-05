// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.getPythonVersion
import com.jetbrains.python.PyBundle
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.target.ui.PyAddCondaPanelModel
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds


@RunWith(Parameterized::class)
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
    private val timeout = 60.seconds

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
  fun testCondaDetection(): Unit =  timeoutRunBlocking(timeout) {
    val model = PyAddCondaPanelModel(null, emptyList(), projectRule.project)
    model.detectConda(coroutineContext)
    val detectedPath = model.condaPathTextBoxRwProp.get()
    if (detectedPath.isNotEmpty()) {
      Assert.assertEquals("Wrong path detected", condaRule.condaPathOnTarget, detectedPath)
    }
  }

  @Test
  fun testCondaCreateNewEnv(): Unit =  timeoutRunBlocking(timeout) {
    val condaName = "someNewCondaEnv"
    val model = PyAddCondaPanelModel(null, emptyList(), projectRule.project)
    model.condaPathTextBoxRwProp.set(condaRule.condaPath.toString())
    model.condaActionCreateNewEnvRadioRwProp.set(true)
    model.condaActionUseExistingEnvRadioRwProp.set(false)

    MatcherAssert.assertThat("No 3.9 suggested", model.languageLevels, hasItem(LanguageLevel.PYTHON39))
    MatcherAssert.assertThat("2.6 suggested", model.languageLevels, not(hasItem(LanguageLevel.PYTHON26)))
    model.newEnvLanguageLevelRwProperty.set(LanguageLevel.PYTHON38)
    Assert.assertNotNull("Empty conda env name didn't lead to validation", model.getValidationError())
    model.newEnvNameRwProperty.set("d     f --- ")
    Assert.assertNotNull("Bad conda name didn't lead to validation", model.getValidationError())
    model.newEnvNameRwProperty.set(condaName)

    val sdk = model.onCondaCreateSdkClicked(coroutineContext, null, targetConfiguration = null).getOrThrow()
    val newName = ((sdk.getOrCreateAdditionalData().flavorAndData.data as PyCondaFlavorData).env.envIdentity as PyCondaEnvIdentity.NamedEnv).envName
    Assert.assertEquals("Wrong conda name", condaName, newName)
  }

  @Test
  fun testCondaCantUseNameUsedAlready(): Unit =  timeoutRunBlocking(timeout) {
    val name = "cond_env_" + Math.random().toString().replace('.', '_')

    // Create env
    PyCondaEnv.createEnv(condaRule.condaCommand,
                         NewCondaEnvRequest.EmptyNamedEnv(LanguageLevel.PYTHON38, name)).getOrThrow()

    val model = PyAddCondaPanelModel(null, emptyList(), projectRule.project)

    // Trying to create env with same name
    model.condaPathTextBoxRwProp.set(condaRule.condaPath.toString())
    model.onLoadEnvsClicked(coroutineContext)
    model.condaActionCreateNewEnvRadioRwProp.set(true)
    model.condaActionUseExistingEnvRadioRwProp.set(false)
    model.newEnvLanguageLevelRwProperty.set(LanguageLevel.PYTHON38)
    model.newEnvNameRwProperty.set(name)
    Assert.assertEquals("Name duplicate should lead to error", PyBundle.message("python.sdk.conda.problem.env.name.used"),
                        model.getValidationError())
  }

  @Test
  fun testCondaUseExistingEnv(): Unit =  timeoutRunBlocking(timeout) {
    val model = PyAddCondaPanelModel(null, emptyList(), projectRule.project)
    model.condaPathTextBoxRwProp.set(condaRule.condaPath.toString())
    model.onLoadEnvsClicked(coroutineContext)
    model.condaActionUseExistingEnvRadioRwProp.set(true)
    model.condaActionCreateNewEnvRadioRwProp.set(false)
    model.condaEnvModel.selectedItem = model.condaEnvModel.getElementAt(0)
    val sdk = model.onCondaCreateSdkClicked(coroutineContext, null, targetConfiguration = null).getOrThrow()
    Assert.assertTrue(getPythonVersion(sdk, LocalTargetEnvironmentRequest())!!.isNotBlank())
    Assert.assertTrue(Path.of(sdk.homePath!!).exists())
  }

  @Test
  fun testCondaModelValidation(): Unit =  timeoutRunBlocking(timeout) {
    val model = PyAddCondaPanelModel(null, emptyList(), projectRule.project)
    Assert.assertNotNull("No validation error, even though path not set", model.getValidationError())

    Assert.assertFalse(model.showCondaPathSetOkButtonRoProp.get())
    model.condaPathTextBoxRwProp.set("Some random path that doesn't exist and doesn't contain conda")
    Assert.assertFalse("Wrong path, but button not disabled", model.showCondaPathSetOkButtonRoProp.get())

    model.condaPathTextBoxRwProp.set("foo/conda")
    Assert.assertTrue("Path that passes regex, but button disabled", model.showCondaPathSetOkButtonRoProp.get())

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

    model.newEnvNameRwProperty.set("SomeEnv-Name")
    Assert.assertNull("Unexpected error", model.getValidationError())
  }

}
