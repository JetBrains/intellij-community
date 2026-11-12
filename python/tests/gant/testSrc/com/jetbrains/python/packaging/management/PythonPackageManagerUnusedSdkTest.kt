// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.Disposer
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.PythonEnvironmentWatchers
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
  private val pyProjectFixture = projectFixture.pyProjectFixture(modulePathFixture)

  // A second project that runs the same environment, see `two projects that run one environment share one listener`.
  private val otherProjectFixture = projectFixture()
  private val otherModulePathFixture = tempPathFixture(prefix = "py-88315-packages-other")
  private val otherPyProjectFixture = otherProjectFixture.pyProjectFixture(otherModulePathFixture)

  @Test
  fun `the manager of an interpreter is cached`(@TempDir home: Path): Unit =
    timeoutRunBlocking(1.minutes) {
      val project = projectFixture.get()
      val interpreter = registerInterpreter("PY-88315 packages cached", home)
      useInterpreter(interpreter)
      val service = project.service<PythonPackageManagerService>()

      val manager = service.forPythonInterpreter(project, interpreter)

      assertThat(service.forPythonInterpreter(project, interpreter)).isSameAs(manager)
    }

  @Test
  fun `the paths of an interpreter in use are watched`(@TempDir home: Path): Unit =
    timeoutRunBlocking(1.minutes) {
      val project = projectFixture.get()
      val interpreter = registerInterpreter("PY-88315 packages watched", home)
      useInterpreter(interpreter)
      val service = project.service<PythonPackageManagerService>()

      service.forPythonInterpreter(project, interpreter)

      service.awaitWatcher(interpreter, watched = true)
    }

  @Test
  fun `the paths of an interpreter that no module uses are not watched`(
    @TempDir home: Path,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    pyProjectFixture.get().residesOnModule
    val interpreter = registerInterpreter("PY-88315 packages of nobody", home)
    val service = project.service<PythonPackageManagerService>()

    service.forPythonInterpreter(project, interpreter)

    // The structure has landed, so an absent watcher is a decision and not a moment before one
    EvoPyProjectModel.getInstance(project).snapshot()
    assertThat(service.impl().watchesInterpreterPaths(interpreter)).isFalse()
  }

  /**
   * The order a new environment is built in: the interpreter is asked about before it belongs to a module.
   */
  @Test
  fun `an interpreter cached before it belongs to a module keeps its manager and gains a watcher`(
    @TempDir home: Path,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val interpreter = registerInterpreter("PY-88315 packages attached later", home)
    val service = project.service<PythonPackageManagerService>()
    val manager = service.forPythonInterpreter(project, interpreter)
    EvoPyProjectModel.getInstance(project).snapshot()
    assertThat(service.impl().watchesInterpreterPaths(interpreter)).isFalse()

    useInterpreter(interpreter)

    service.awaitWatcher(interpreter, watched = true)
    assertThat(service.forPythonInterpreter(project, interpreter)).describedAs("The manager a caller holds must survive the move").isSameAs(manager)
  }

  @Test
  fun `an interpreter loses its watcher when the module moves to another one`(
    @TempDir home: Path,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val interpreter = registerInterpreter("PY-88315 packages left behind", home.resolve("old"))
    useInterpreter(interpreter)
    val service = project.service<PythonPackageManagerService>()
    val manager = service.forPythonInterpreter(project, interpreter)
    service.awaitWatcher(interpreter, watched = true)

    useInterpreter(registerInterpreter("PY-88315 packages the new one", home.resolve("new")))

    service.awaitWatcher(interpreter, watched = false)
    assertThat(service.forPythonInterpreter(project, interpreter)).describedAs("The manager a caller holds must survive the move").isSameAs(manager)
  }

  /**
   * The shared listener of `PythonEnvironmentWatchers` reads the roots of the SDK of each watch on every file event of
   * the whole application. A watch parented to something that outlives the interpreter therefore reads a disposed SDK,
   * which is what PY-88315 did to the light fixtures that rebuild their interpreter between tests.
   */
  @Test
  fun `the watcher of an interpreter goes when the interpreter goes`(
    @TempDir home: Path,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val interpreter = registerInterpreter("PY-88315 packages of a doomed interpreter", home)
    useInterpreter(interpreter)
    val service = project.service<PythonPackageManagerService>()
    service.forPythonInterpreter(project, interpreter)
    service.awaitWatcher(interpreter, watched = true)

    val watcherGone = AtomicBoolean()
    Disposer.register(service.impl().interpreterPathsWatcher(interpreter)!!, Disposable { watcherGone.set(true) })
    PythonInterpreterRegistry.getInstance(project).removePythonInterpreter(pyProjectFixture.get(), interpreter)

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
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val interpreter = registerInterpreter("PY-89433 packages of a closed project", home)
    useInterpreter(interpreter)
    val service = project.service<PythonPackageManagerService>()
    service.forPythonInterpreter(project, interpreter)
    service.awaitWatcher(interpreter, watched = true)

    val watcherGone = AtomicBoolean()
    Disposer.register(service.impl().interpreterPathsWatcher(interpreter)!!, Disposable { watcherGone.set(true) })
    Disposer.dispose(service.impl())

    assertThat(watcherGone.get()).describedAs("The watcher outlived the project that owns it").isTrue()
  }

  /**
   * The removal of an entry has the SDK as its parent, and it holds the manager and through it the project. An SDK can
   * outlive the project, such as an SDK of the SDK table. Closing the project therefore has to take the removal too.
   */
  @Test
  fun `the removal of an entry goes when the project goes`(
    @TempDir home: Path,
  ): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val interpreter = registerInterpreter("PY-93818 packages of a closed project", home)
    val service = project.service<PythonPackageManagerService>()
    service.forPythonInterpreter(project, interpreter)

    val removalGone = AtomicBoolean()
    Disposer.register(service.impl().sdkRemoval(interpreter)!!, Disposable { removalGone.set(true) })
    Disposer.dispose(service.impl())

    assertThat(removalGone.get()).describedAs("The removal outlived the project that owns it").isTrue()
  }

  private fun PythonPackageManagerService.impl(): PythonPackageManagerServiceImpl = this as PythonPackageManagerServiceImpl

  /**
   * Each project watches the environment of its interpreter, see PythonEnvironmentWatchers. Two projects whose
   * interpreters run one binary share one file listener, and it goes when no project watches the environment.
   */
  @Test
  fun `two projects that run one environment share one listener`(@TempDir home: Path): Unit =
    timeoutRunBlocking(1.minutes) {
      val watchers = PythonEnvironmentWatchers.getInstance()
      val listenersBefore = watchers.listenerCountForTests()
      val interpreter = registerInterpreter("PY-88315 shared env", home)
      useInterpreter(interpreter)
      val other = registerInterpreter("PY-88315 shared env", home, otherProjectFixture.get(), otherPyProjectFixture.get())
      otherPyProjectFixture.get().setPythonInterpreter(other).orThrow()
      val service = projectFixture.get().service<PythonPackageManagerService>()
      val otherService = otherProjectFixture.get().service<PythonPackageManagerService>()

      service.forPythonInterpreter(projectFixture.get(), interpreter)
      otherService.forPythonInterpreter(otherProjectFixture.get(), other)
      service.awaitWatcher(interpreter, watched = true)
      otherService.awaitWatcher(other, watched = true)

      assertThat(watchers.listenerCountForTests() - listenersBefore).isEqualTo(1)

      val moved = registerInterpreter("PY-88315 shared env, moved", home.resolve("moved"))
      useInterpreter(moved)
      otherPyProjectFixture.get().setPythonInterpreter(null).orThrow()
      service.awaitWatcher(interpreter, watched = false)
      otherService.awaitWatcher(other, watched = false)
      // The project starts the manager of the interpreter it uses, so the moved one has its own listener.
      service.awaitWatcher(moved, watched = true)

      assertThat(watchers.listenerCountForTests() - listenersBefore).describedAs("The shared listener went").isEqualTo(1)
    }

  /** The structure is published on a flow, so a watcher appears and goes after the change, not with it. */
  private suspend fun PythonPackageManagerService.awaitWatcher(interpreter: PythonInterpreter, watched: Boolean) {
    while (impl().watchesInterpreterPaths(interpreter) != watched) {
      delay(50.milliseconds)
    }
  }

  /** Registers an SDK and returns its interpreter, as a caller of the service holds one. */
  /**
   * An interpreter of the Python project of the fixture whose binary is under [home]. The registry holds it, and its
   * SDK is in no SDK table. The project closes with the fixture, and the registry disposes the SDK with it.
   */
  private suspend fun registerInterpreter(
    name: String,
    home: Path,
    project: Project = projectFixture.get(),
    pyProject: PyProject = pyProjectFixture.get(),
  ): PythonInterpreter {
    val sdk = ProjectJdkImpl(name, PythonSdkType.getInstance())
    val modificator = sdk.sdkModificator
    modificator.homePath = home.resolve("bin").resolve("python").toString()
    modificator.sdkAdditionalData = PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()), home)
    WriteAction.runAndWait<RuntimeException> { modificator.commitChanges() }
    return PythonInterpreterRegistry.getInstance(project).addMockPythonInterpreter(pyProject, sdk)
  }

  /**
   * Makes [interpreter] the interpreter of the Python project. Its interpreter library entry names it, so a later
   * platform write of the module SDK would not move the module.
   */
  private suspend fun useInterpreter(interpreter: PythonInterpreter) {
    pyProjectFixture.get().setPythonInterpreter(interpreter).orThrow()
  }


}
