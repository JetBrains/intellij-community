// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A background process asks for an update of an interpreter whenever something changes under its paths. Such an update
 * generates skeletons and scans packages, which starts the interpreter, so it must run only for an interpreter that the
 * project uses. See PY-88315.
 */
@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PythonSdkUpdaterInUseTest {
  private val projectFixture = projectFixture()
  private val moduleFixture = projectFixture.pyModuleFixture("py-88315")

  @Test
  fun `an interpreter that no module uses is not in use`(@TestDisposable disposable: Disposable) {
    val project = projectFixture.get()
    moduleFixture.get()
    val sdk = registerSdk("PY-88315 registered only", disposable)

    assertThat(PythonSdkUpdater.isSdkInUse(sdk, project)).isFalse()
    assertThat(PythonSdkUpdater.scheduleBackgroundUpdate(sdk, project))
      .describedAs("A background update started for an interpreter that the project does not use")
      .isFalse()
  }

  @Test
  fun `the interpreter of a module is in use`(@TestDisposable disposable: Disposable) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val sdk = registerSdk("PY-88315 of the module", disposable)
    module.useSdk(sdk)

    assertThat(PythonSdkUpdater.isSdkInUse(sdk, project)).isTrue()
    assertThat(PythonSdkUpdater.scheduleBackgroundUpdate(sdk, project))
      .describedAs("The project uses the interpreter, so its background update must be scheduled")
      .isTrue()
  }

  @Test
  fun `an interpreter stops being in use when the module moves to another one`(@TestDisposable disposable: Disposable) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val sdk = registerSdk("PY-88315 replaced", disposable)
    module.useSdk(sdk)
    assertThat(PythonSdkUpdater.isSdkInUse(sdk, project)).isTrue()

    val other = registerSdk("PY-88315 the new one", disposable)
    module.useSdk(other)

    assertThat(PythonSdkUpdater.isSdkInUse(sdk, project)).isFalse()
    assertThat(PythonSdkUpdater.isSdkInUse(other, project)).isTrue()
  }

  /** A module entry names its interpreter, and the name resolves through the table, so the table must know it. */
  private fun registerSdk(name: String, disposable: Disposable): Sdk {
    val sdk = ProjectJdkImpl(name, PythonSdkType.getInstance())
    WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, disposable) }
    return sdk
  }

  private fun Module.useSdk(sdk: Sdk) {
    WriteAction.runAndWait<RuntimeException> { ModuleRootModificationUtil.setModuleSdk(this, sdk) }
  }
}
