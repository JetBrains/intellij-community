// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.configuration.PyLocalInterpreterEditDialog
import com.jetbrains.python.configuration.PyLocalInterpreterEditModel
import com.jetbrains.python.configuration.PyLocalInterpreterHandle
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Covers the persistence sequence extracted from [PyLocalInterpreterEditDialog]:
 * the model exposes editable state (`homePath`, `associated`) plus an `apply` that mutates the SDK
 * through the shared [setAssociationToModule] helper and the standard [Sdk.sdkModificator] path.
 *
 * Path-change branches are exercised through the "no change → no `PythonSdkUpdater`" path only,
 * because a home-path mutation requires a valid mock venv structure that this test's mock SDK does
 * not carry — the launcher-level UI smoke covers the real path swap.
 */
@Subsystems.Interpreters
@Layers.Functional
@TestApplication
class PyLocalInterpreterEditModelTest {

  companion object {
    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture()
    // Materializes a real content root under the module so `Module.baseDir` resolves to a
    // non-null path when the tests call `setAssociationToModule`.
    private val moduleRootFixture = moduleFixture.sourceRootFixture()
  }

  private val project get() = projectFixture.get()

  /** Reads [moduleRootFixture] first so the content root is in place before the module is used. */
  private val module: Module
    get() {
      moduleRootFixture.get()
      return moduleFixture.get()
    }

  private lateinit var sdk: Sdk

  /**
   * `PythonMockSdk.create()` builds an SDK with proper [com.jetbrains.python.sdk.PythonSdkAdditionalData]
   * — the association helpers ([com.jetbrains.python.sdk.setAssociationToPath]) mutate that data
   * directly, so the `pyMockSdkFixture` shortcut is unusable here (it skips additional data and
   * `setAssociationToPath` throws "created by buggy code"). We build the SDK per test and register
   * it in `ProjectJdkTable` under the test disposable so cleanup happens automatically.
   */
  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    sdk = PythonMockSdk.create()
    runWriteActionAndWait {
      ProjectJdkTable.getInstance().addJdk(sdk, disposable)
    }
  }

  @Test
  fun `initial state reflects the constructor arguments`(): Unit = runBlocking {
    val model = buildModel()
    assertEquals(Path.of(sdk.homePath!!), model.initialHomePath)
    assertFalse(model.initialAssociated)
    assertTrue(model.canAssociate)
  }

  @Test
  fun `apply is a no-op when nothing changed`(): Unit = runBlocking {
    val model = buildModel()
    val changed = model.apply()
    assertFalse(changed)
    assertNull(sdk.associatedModulePath)
  }

  @Test
  fun `apply associates the SDK with the target module`(): Unit = runBlocking {
    val model = buildModel()
    model.associated = true

    val changed = model.apply()

    assertTrue(changed)
    assertEquals(module.baseDir?.path, sdk.associatedModulePath)
  }

  @Test
  fun `apply clears an existing association`(): Unit = runBlocking {
    sdk.setAssociationToModule(module)
    assertNotNull(sdk.associatedModulePath)

    val model = buildModel()
    assertTrue(model.initialAssociated)
    model.associated = false

    val changed = model.apply()

    assertTrue(changed)
    assertNull(sdk.associatedModulePath)
  }

  @Test
  fun `can associate stays true after the checkbox flips back to unchecked`(): Unit = runBlocking {
    sdk.setAssociationToModule(module)
    val model = buildModel()
    // canAssociate reflects the *initial* state so the checkbox does not vanish when the user
    // temporarily unticks it before OK.
    assertTrue(model.canAssociate)
    model.associated = false
    assertTrue(model.canAssociate)
  }

  // Mock SDKs don't resolve a `PythonEnvironment`, so the production `createOrNull` factory always
  // returns `null` here — use the `@TestOnly` factory that fabricates the handle from the mock's
  // `homePath` directly. `!!` on `sdk.homePath` throws NPE if the mock is broken (a real bug).
  private fun buildModel(): PyLocalInterpreterEditModel {
    val handle = PyLocalInterpreterHandle.testOnly(sdk, Path.of(sdk.homePath!!))
    return PyLocalInterpreterEditModel(project, handle, module)
  }
}
