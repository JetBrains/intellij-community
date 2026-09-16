// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlinx.coroutines.delay
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * A package manager watches the paths of its interpreter and asks for a full update on a change, and that update starts
 * the interpreter. Only an interpreter that a module of the project uses may ask for that, so the watcher follows the
 * interpreter into use and out of it. The manager itself stays, because a caller holds it. See PY-88315.
 */
@TestApplication
@Subsystems.Packaging
@Layers.Functional
internal class PythonPackageManagerUnusedSdkTest {
  private val projectFixture = projectFixture()
  // With a content root: a module without one is no `PyProject`, so `EvoPyProjectModel` does not see it at all
  private val modulePathFixture = tempPathFixture(prefix = "py-88315-packages")
  private val moduleFixture = projectFixture.pyModuleFixture(modulePathFixture, addPathToSourceRoot = true)

  @Test
  fun `the manager of an interpreter is cached`(@TempDir home: Path, @TestDisposable disposable: Disposable) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val sdk = registerSdk("PY-88315 packages cached", home, disposable)
    module.useSdk(sdk)
    val service = project.service<PythonPackageManagerService>()

    val manager = service.forSdk(project, sdk)

    assertThat(service.forSdk(project, sdk)).isSameAs(manager)
  }

  @Test
  fun `the paths of an interpreter in use are watched`(@TempDir home: Path, @TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking(1.minutes) {
      val project = projectFixture.get()
      val module = moduleFixture.get()
      val sdk = registerSdk("PY-88315 packages watched", home, disposable)
      module.useSdk(sdk)
      val service = project.service<PythonPackageManagerService>()

      service.forSdk(project, sdk)

      service.awaitWatcher(sdk, watched = true)
    }

  @Test
  fun `the paths of an interpreter that no module uses are not watched`(
    @TempDir home: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    moduleFixture.get()
    val sdk = registerSdk("PY-88315 packages of nobody", home, disposable)
    val service = project.service<PythonPackageManagerService>()

    service.forSdk(project, sdk)

    // The structure has landed, so an absent watcher is a decision and not a moment before one
    project.service<EvoPyProjectModel>().snapshot()
    assertThat(service.impl().watchesInterpreterPaths(sdk)).isFalse()
  }

  /**
   * The order a new environment is built in: the interpreter is asked about before it belongs to a module.
   */
  @Test
  fun `an interpreter cached before it belongs to a module keeps its manager and gains a watcher`(
    @TempDir home: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val sdk = registerSdk("PY-88315 packages attached later", home, disposable)
    val service = project.service<PythonPackageManagerService>()
    val manager = service.forSdk(project, sdk)
    project.service<EvoPyProjectModel>().snapshot()
    assertThat(service.impl().watchesInterpreterPaths(sdk)).isFalse()

    module.useSdk(sdk)

    service.awaitWatcher(sdk, watched = true)
    assertThat(service.forSdk(project, sdk)).describedAs("The manager a caller holds must survive the move").isSameAs(manager)
  }

  @Test
  fun `an interpreter loses its watcher when the module moves to another one`(
    @TempDir home: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val sdk = registerSdk("PY-88315 packages left behind", home.resolve("old"), disposable)
    module.useSdk(sdk)
    val service = project.service<PythonPackageManagerService>()
    val manager = service.forSdk(project, sdk)
    service.awaitWatcher(sdk, watched = true)

    module.useSdk(registerSdk("PY-88315 packages the new one", home.resolve("new"), disposable))

    service.awaitWatcher(sdk, watched = false)
    assertThat(service.forSdk(project, sdk)).describedAs("The manager a caller holds must survive the move").isSameAs(manager)
  }

  /**
   * `runOnChangeUnderInterpreterPaths` states that its parent disposable must not outlive the interpreter: its listener
   * throws for a disposed interpreter, and it does so for every file event of the whole application. A watcher parented
   * to something that outlives the interpreter therefore breaks the VFS for everyone, which is what PY-88315 did to the
   * light fixtures that rebuild their interpreter between tests.
   */
  @Test
  fun `the watcher of an interpreter goes when the interpreter goes`(
    @TempDir home: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val sdk = registerSdk("PY-88315 packages of a doomed interpreter", home, disposable)
    module.useSdk(sdk)
    val service = project.service<PythonPackageManagerService>()
    service.forSdk(project, sdk)
    service.awaitWatcher(sdk, watched = true)

    val watcherGone = AtomicBoolean()
    Disposer.register(service.impl().interpreterPathsWatcher(sdk)!!, Disposable { watcherGone.set(true) })
    WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().removeJdk(sdk) }

    assertThat(watcherGone.get()).describedAs("The watcher outlived the interpreter it watches").isTrue()
  }

  private fun PythonPackageManagerService.impl(): PythonPackageManagerServiceImpl = this as PythonPackageManagerServiceImpl

  /** The structure is published on a flow, so a watcher appears and goes after the change, not with it. */
  private suspend fun PythonPackageManagerService.awaitWatcher(sdk: Sdk, watched: Boolean) {
    while (impl().watchesInterpreterPaths(sdk) != watched) {
      delay(50.milliseconds)
    }
  }

  private fun registerSdk(name: String, home: Path, disposable: Disposable): Sdk {
    val sdk = ProjectJdkImpl(name, PythonSdkType.getInstance())
    val modificator = sdk.sdkModificator
    modificator.homePath = home.resolve("bin").resolve("python").toString()
    modificator.sdkAdditionalData = PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()), home)
    WriteAction.runAndWait<RuntimeException> {
      modificator.commitChanges()
      ProjectJdkTable.getInstance().addJdk(sdk, disposable)
    }
    return sdk
  }

  private fun Module.useSdk(sdk: Sdk) {
    WriteAction.runAndWait<RuntimeException> { ModuleRootModificationUtil.setModuleSdk(this, sdk) }
  }
}
