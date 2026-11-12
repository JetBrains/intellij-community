// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.openapi.Disposable
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 * A commit of an SDK reports no event of the SDK table, only a change of its entity. [PythonInterpreterRegistry] builds
 * its interpreters again on it, so an interpreter follows what its SDK says now, such as a new target or a new owner.
 * This is the legacy flow: the interpreter library flow reads no SDK table, and an owner never changes there.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "false")
@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PythonInterpreterRegistrySdkChangeTest {
  private val projectFixture = projectFixture()

  @Test
  fun `the interpreter follows a commit that changes its SDK`(
    @TempDir home: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
    val sdk = ProjectJdkImpl("PY-93079 SDK change", PythonSdkType.getInstance())
    val firstOwner = home.resolve("first")
    val secondOwner = home.resolve("second")
    edtWriteAction {
      val modificator = sdk.sdkModificator
      modificator.homePath = home.resolve("bin").resolve("python").toString()
      modificator.sdkAdditionalData = PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()), firstOwner)
      modificator.commitChanges()
      ProjectJdkTable.getInstance().addJdk(sdk, disposable)
    }
    awaitOwner(registry, sdk, firstOwner)

    edtWriteAction {
      val modificator = sdk.sdkModificator
      (modificator.sdkAdditionalData as PythonSdkAdditionalData).associatedModulePath = secondOwner.toString()
      modificator.commitChanges()
    }

    awaitOwner(registry, sdk, secondOwner)
    assertThat(registry.interpreterOf(sdk)?.ownerDir).isEqualTo(secondOwner)
  }

  /** Waits until the registry holds an interpreter of [sdk] owned by [owner]. The test timeout ends a wait that never does. */
  private suspend fun awaitOwner(registry: PythonInterpreterRegistry, sdk: ProjectJdkImpl, owner: Path) {
    while (registry.interpreterOf(sdk)?.ownerDir != owner) {
      delay(50.milliseconds)
    }
  }
}
