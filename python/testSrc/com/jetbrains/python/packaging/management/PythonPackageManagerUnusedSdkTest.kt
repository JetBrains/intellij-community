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
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.pythonInterpreterAsync

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
  fun `the manager of an interpreter is cached`(@TempDir home: Path, @TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking(1.minutes) {
      val project = projectFixture.get()
      val module = moduleFixture.get()
      val interpreter = registerInterpreter("PY-88315 packages cached", home, disposable)
      module.useInterpreter(interpreter)
      val service = project.service<PythonPackageManagerService>()

      val manager = service.forPythonInterpreter(project, interpreter)

      assertThat(service.forPythonInterpreter(project, interpreter)).isSameAs(manager)
    }

  @Test
  fun `the paths of an interpreter in use are watched`(@TempDir home: Path, @TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking(1.minutes) {
      val project = projectFixture.get()
      val module = moduleFixture.get()
      val interpreter = registerInterpreter("PY-88315 packages watched", home, disposable)
      module.useInterpreter(interpreter)
      val service = project.service<PythonPackageManagerService>()

      service.forPythonInterpreter(project, interpreter)

      service.awaitWatcher(interpreter, watched = true)
    }

  @Test
  fun `the paths of an interpreter that no module uses are not watched`(
    @TempDir home: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    moduleFixture.get()
    val interpreter = registerInterpreter("PY-88315 packages of nobody", home, disposable)
    val service = project.service<PythonPackageManagerService>()

    service.forPythonInterpreter(project, interpreter)

    // The structure has landed, so an absent watcher is a decision and not a moment before one
    EvoPyProjectModel.getInstance(project).snapshot()
    assertThat(service.impl().watchesInterpreterPaths(interpreter.sdk)).isFalse()
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
    val interpreter = registerInterpreter("PY-88315 packages attached later", home, disposable)
    val service = project.service<PythonPackageManagerService>()
    val manager = service.forPythonInterpreter(project, interpreter)
    EvoPyProjectModel.getInstance(project).snapshot()
    assertThat(service.impl().watchesInterpreterPaths(interpreter.sdk)).isFalse()

    module.useInterpreter(interpreter)

    service.awaitWatcher(interpreter, watched = true)
    assertThat(service.forPythonInterpreter(project, interpreter)).describedAs("The manager a caller holds must survive the move").isSameAs(manager)
  }

  @Test
  fun `an interpreter loses its watcher when the module moves to another one`(
    @TempDir home: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val interpreter = registerInterpreter("PY-88315 packages left behind", home.resolve("old"), disposable)
    module.useInterpreter(interpreter)
    val service = project.service<PythonPackageManagerService>()
    val manager = service.forPythonInterpreter(project, interpreter)
    service.awaitWatcher(interpreter, watched = true)

    module.useInterpreter(registerInterpreter("PY-88315 packages the new one", home.resolve("new"), disposable))

    service.awaitWatcher(interpreter, watched = false)
    assertThat(service.forPythonInterpreter(project, interpreter)).describedAs("The manager a caller holds must survive the move").isSameAs(manager)
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
    val interpreter = registerInterpreter("PY-88315 packages of a doomed interpreter", home, disposable)
    module.useInterpreter(interpreter)
    val service = project.service<PythonPackageManagerService>()
    service.forPythonInterpreter(project, interpreter)
    service.awaitWatcher(interpreter, watched = true)

    val watcherGone = AtomicBoolean()
    Disposer.register(service.impl().interpreterPathsWatcher(interpreter.sdk)!!, Disposable { watcherGone.set(true) })
    WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().removeJdk(interpreter.sdk) }

    assertThat(watcherGone.get()).describedAs("The watcher outlived the interpreter it watches").isTrue()
  }

  /**
   * A watcher is parented to the interpreter, which lives as long as the application, and the change it runs holds the
   * service and through it the project. Closing the project therefore has to take the watcher, or the interpreter keeps
   * the closed project alive (PY-89433).
   */
  @Test
  fun `the watcher of an interpreter goes when the project goes`(
    @TempDir home: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val interpreter = registerInterpreter("PY-89433 packages of a closed project", home, disposable)
    module.useInterpreter(interpreter)
    val service = project.service<PythonPackageManagerService>()
    service.forPythonInterpreter(project, interpreter)
    service.awaitWatcher(interpreter, watched = true)

    val watcherGone = AtomicBoolean()
    Disposer.register(service.impl().interpreterPathsWatcher(interpreter.sdk)!!, Disposable { watcherGone.set(true) })
    Disposer.dispose(service.impl())

    assertThat(watcherGone.get()).describedAs("The watcher outlived the project that owns it").isTrue()
  }

  private fun PythonPackageManagerService.impl(): PythonPackageManagerServiceImpl = this as PythonPackageManagerServiceImpl

  /** The structure is published on a flow, so a watcher appears and goes after the change, not with it. */
  private suspend fun PythonPackageManagerService.awaitWatcher(interpreter: PythonInterpreter, watched: Boolean) {
    while (impl().watchesInterpreterPaths(interpreter.sdk) != watched) {
      delay(50.milliseconds)
    }
  }

  /** Registers an SDK and returns its interpreter, as a caller of the service holds one. */
  private suspend fun registerInterpreter(name: String, home: Path, disposable: Disposable): PythonInterpreter {
    val sdk = ProjectJdkImpl(name, PythonSdkType.getInstance())
    val modificator = sdk.sdkModificator
    modificator.homePath = home.resolve("bin").resolve("python").toString()
    modificator.sdkAdditionalData = PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()), home)
    WriteAction.runAndWait<RuntimeException> {
      modificator.commitChanges()
      ProjectJdkTable.getInstance().addJdk(sdk, disposable)
    }
    return sdk.pythonInterpreterAsync()
  }

  private fun Module.useInterpreter(interpreter: PythonInterpreter) {
    WriteAction.runAndWait<RuntimeException> { ModuleRootModificationUtil.setModuleSdk(this, interpreter.sdk) }
  }

  /** The SDK of the interpreter, for the platform calls and the watcher checks, which take one. */
  @Suppress("DEPRECATION")
  private val PythonInterpreter.sdk: Sdk get() = getSdkAPI()

}
