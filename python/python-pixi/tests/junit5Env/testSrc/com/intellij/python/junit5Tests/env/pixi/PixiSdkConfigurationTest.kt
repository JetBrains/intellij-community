// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.idea.TestFor
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pixi.backend.PIXI_TOOL_ID
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.project.PixiPyProjectManager
import com.intellij.python.pixi.backend.sdk.PixiSdkConfiguration
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.pyproject.model.api.InterpreterConfigurationResult
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.SdkForModuleConfigInstruction
import com.intellij.python.pyproject.model.api.autoConfigureSdkExistingOnly
import com.intellij.python.pyproject.model.api.configureSdkIfNeeded
import com.intellij.python.pyproject.model.api.getModuleSdkState
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.pyproject.model.internal.platformBridge.rebuildPyProjectModelForTest
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.backend.detectedHomePath
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isFailure
import com.jetbrains.python.isSuccess
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfoWithInterpreterCreator
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
@ExtendWith(PythonFactoryExtension::class, LeakedProcessReporterExtension::class)
internal class PixiSdkConfigurationTest {
  private val rootFixture = tempPathFixture()
  private val projectFixture = projectFixture(rootFixture)
  private val rootProjectFixture = projectFixture.pyProjectFixture(rootFixture)
  private val childPathFixture = projectFixture.pathInProjectFixture(Path.of("child"))
  private val childProjectFixture = projectFixture.pyProjectFixture(childPathFixture)

  @Test
  fun `the plugin registers a Pixi SDK configurator`() {
    assertThat(PyProjectSdkConfigurationExtension.EP_NAME.extensionList.filterIsInstance<PixiSdkConfiguration>()).hasSize(1)
    assertThat(PixiSdkConfiguration().potentialDependencyFiles).containsExactlyInAnyOrder("pixi.toml", "pyproject.toml")
  }

  @ParameterizedTest
  @ValueSource(strings = ["[project]\nname = 'ordinary'\n", "[tool.pixi.workspace\n"])
  fun `ordinary or malformed pyprojects do not offer Pixi setup`(manifest: String): Unit = timeoutRunBlocking {
    rootFixture.get().resolve("pyproject.toml").writeText(manifest)
    val pyProject = rootProjectFixture.get()
    val configurator = PixiSdkConfiguration()

    assertThat(configurator.isExclusiveFor(pyProject)).isFalse()
    assertThat(configurator.checkEnvironmentAndPrepareSdkCreator(pyProject, emptyList())).isNull()
  }

  @Test
  fun `a child uses the workspace model to inherit its interpreter`(): Unit = timeoutRunBlocking {
    rootFixture.get().resolve("pyproject.toml").writeText("[tool.pixi.workspace]\nchannels = ['conda-forge']\n")
    val parent = rootProjectFixture.get()
    val child = childProjectFixture.get()
    child.baseDir.resolve("pyproject.toml").writeText("[project]\nname = 'child'\nversion = '0.1.0'\n")
    rebuildPyProjectModelForTest(projectFixture.get())
    val configurator = PixiSdkConfiguration()

    assertThat(configurator.isExclusiveFor(child)).isTrue()
    val state = child.getModuleSdkState(fresh = true)
    assertThat(state).asInstanceOf(type(ModuleSdkState.NoSdk::class.java))
      .extracting { it.sdkConfigInstruction }.asInstanceOf(type(SdkForModuleConfigInstruction.SameAs::class.java))
      .extracting { it.parent }.isEqualTo(parent)
    val snapshot = EvoPyProjectModel.getInstance(projectFixture.get()).snapshotFlow().first { snapshot ->
      snapshot.workspaces.any { workspace -> workspace.members.map { it.pyProject }.toSet() == setOf(parent, child) }
    }
    assertThat(snapshot.workspaces).hasSize(1)
    assertThat(snapshot.workspaces.single().root.pyProject).isEqualTo(parent)
  }

  @Test
  fun `an explicit nested workspace stays separate in the model`(): Unit = timeoutRunBlocking {
    rootFixture.get().resolve("pyproject.toml").writeText("[tool.pixi.workspace]\nchannels = ['conda-forge']\n")
    val parent = rootProjectFixture.get()
    val child = childProjectFixture.get()
    child.baseDir.resolve("pyproject.toml").writeText("[tool.pixi.workspace]\nchannels = ['conda-forge']\n")
    rebuildPyProjectModelForTest(projectFixture.get())

    assertThat(child.getModuleSdkState(fresh = true)).asInstanceOf(type(ModuleSdkState.NoSdk::class.java))
      .extracting { it.sdkConfigInstruction }.isNotInstanceOf(SdkForModuleConfigInstruction.SameAs::class.java)
    val snapshot = EvoPyProjectModel.getInstance(projectFixture.get()).snapshotFlow().first { snapshot ->
      snapshot.workspaces.map { it.root.pyProject }.toSet() == setOf(parent, child)
    }
    assertThat(snapshot.workspaces).allSatisfy { workspace ->
      assertThat(workspace.members.map { it.pyProject }).containsExactly(workspace.root.pyProject)
    }
  }

  @Test
  fun `removing a workspace separates its former members`(): Unit = timeoutRunBlocking {
    val manifest = rootFixture.get().resolve("pyproject.toml")
    val projectTable = "[project]\nname = 'root'\nversion = '0.1.0'\n"
    manifest.writeText(projectTable + "[tool.pixi.workspace]\nchannels = ['conda-forge']\n")
    val parent = rootProjectFixture.get()
    val child = childProjectFixture.get()
    child.baseDir.resolve("pyproject.toml").writeText("[project]\nname = 'child'\nversion = '0.1.0'\n")
    val model = EvoPyProjectModel.getInstance(projectFixture.get())
    rebuildPyProjectModelForTest(projectFixture.get())
    model.snapshotFlow().first { snapshot ->
      snapshot.workspaces.any { workspace -> workspace.members.map { it.pyProject }.toSet() == setOf(parent, child) }
    }

    manifest.writeText(projectTable)
    rebuildPyProjectModelForTest(projectFixture.get())

    val snapshot = model.snapshotFlow().first { snapshot ->
      snapshot.workspaces.map { it.root.pyProject }.toSet() == setOf(parent, child)
    }
    assertThat(snapshot.workspaces).allSatisfy { workspace ->
      assertThat(workspace.members.map { it.pyProject }).containsExactly(workspace.root.pyProject)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  @TestFor(issues = ["PY-91735"])
  fun `a generated package configures Python from the enclosing workspace`(manifestName: String, factory: PyEnvironmentFactory): Unit =
    timeoutRunBlocking(5.minutes) {
      val root = rootFixture.get()
      val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
      root.resolve(manifestName).writeText(
        "[${prefix}workspace]\nchannels = ['conda-forge']\n" +
        "platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']\n" +
        "[${prefix}dependencies]\npython = '3.13.*'\n"
      )
      if (manifestName == "pixi.toml") {
        root.resolve("pyproject.toml").writeText("[project]\nname = 'root'\nversion = '0.1.0'\n")
      }
      val parent = rootProjectFixture.get()
      withExecutable(factory) {
        assertThat(parent.configureSdkIfNeeded()).isInstanceOf(InterpreterConfigurationResult.Configured::class.java)
        val result = PixiPyProjectManager().createProject(root, "child")
        assertThat(result.isSuccess).isTrue()
        result.getOrThrow()
        val child = childProjectFixture.get()
        rebuildPyProjectModelForTest(projectFixture.get())
        val manifest = child.baseDir.resolve("pyproject.toml")
        val original = manifest.readText()
        val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
        try {
          val options = PyProjectSdkConfigurationExtension.findAllSorted(child, emptyList())
          assertThat(options.map { it.toolId }).containsExactly(PIXI_TOOL_ID)
          assertThat(options.single().createSdkInfo).isInstanceOf(CreateInterpreterInfo.ExistingEnv::class.java)
          assertThat(child.baseDir.resolve(".pixi/envs/default")).doesNotExist()
          assertThat(registry.interpreters(child)).isEmpty()

          assertThat(child.configureSdkIfNeeded { autoConfigureSdkExistingOnly() })
            .isInstanceOf(InterpreterConfigurationResult.Configured::class.java)

          val interpreter = checkNotNull(child.getInterpreter())
          assertThat(interpreter.detectedHomePath).isEqualTo(root.resolve(".pixi/envs/default"))
          assertThat(interpreter.ref.envRef).isEqualTo(PyEnvRef("default"))
          assertThat(parent.getInterpreter()).isEqualTo(interpreter)
          assertThat(manifest.readText()).isEqualTo(original)
          assertThat(child.baseDir.resolve("pixi.toml")).doesNotExist()
          assertThat(child.baseDir.resolve(".pixi")).doesNotExist()

          val existing = PyProjectSdkConfigurationExtension.findAllSorted(child, emptyList())
          assertThat(existing.map { it.toolId }).containsExactly(PIXI_TOOL_ID)
          assertThat(existing.single().createSdkInfo).isInstanceOf(CreateInterpreterInfo.ExistingEnv::class.java)
          assertThat(registry.interpreters(child)).isEmpty()
          assertThat(registry.interpreters(parent)).containsExactly(interpreter)
        }
        finally {
          for (pyProject in listOf(child, parent)) {
            for (interpreter in registry.interpreters(pyProject)) registry.removePythonInterpreter(pyProject, interpreter)
          }
        }
      }
    }

  @Test
  @TestFor(issues = ["PY-91735"])
  fun `a new package is installed into an existing workspace environment`(factory: PyEnvironmentFactory): Unit =
    timeoutRunBlocking(5.minutes) {
      val root = rootFixture.get()
      root.resolve("pyproject.toml").writeText(
        "[tool.pixi.workspace]\nchannels = ['conda-forge']\n" +
        "platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']\n" +
        "[tool.pixi.dependencies]\npython = '3.13.*'\n"
      )
      val parent = rootProjectFixture.get()
      withExecutable(factory) {
        val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
        try {
          assertThat(parent.configureSdkIfNeeded()).isInstanceOf(InterpreterConfigurationResult.Configured::class.java)
          val parentInterpreter = checkNotNull(parent.getInterpreter())
          val result = PyProjectManager.forPythonInterpreter(parentInterpreter).createProject(root, "child")
          assertThat(result.isSuccess).isTrue()
          result.getOrThrow()
          val child = childProjectFixture.get()
          rebuildPyProjectModelForTest(projectFixture.get())
          assertThat(child.configureSdkIfNeeded { autoConfigureSdkExistingOnly() })
            .isInstanceOf(InterpreterConfigurationResult.Configured::class.java)
          val interpreter = checkNotNull(child.getInterpreter())
          assertThat(interpreter).isEqualTo(parentInterpreter)
          assertThat(parent.getInterpreter()).isEqualTo(parentInterpreter)
          val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)

          val imported = ExecService().execGetStdout(binary, Args("-c", "import child; print(child.__file__)"))

          assertThat(imported.isSuccess).isTrue()
          assertThat(imported.getOrThrow().trim()).isEqualTo(child.baseDir.resolve("src/child/__init__.py").pathString)
          assertThat(child.baseDir.resolve(".pixi")).doesNotExist()
        }
        finally {
          for (pyProject in listOf(childProjectFixture.get(), parent)) {
            for (interpreter in registry.interpreters(pyProject)) registry.removePythonInterpreter(pyProject, interpreter)
          }
        }
      }
    }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `a child configures Python from the enclosing workspace without changing either manifest`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val root = rootFixture.get()
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val manifest = root.resolve(manifestName)
    val original = """
      # Keep the workspace comment.
      [${prefix}workspace]
      channels = ['conda-forge']
      platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']
      [${prefix}dependencies]
      python = '3.13.*'

    """.trimIndent()
    manifest.writeText(original)
    if (manifestName == "pixi.toml") {
      root.resolve("pyproject.toml").writeText("[project]\nname = 'root'\nversion = '0.1.0'\n")
    }
    val childPath = root.resolve("child").createDirectories()
    val childManifest = childPath.resolve("pyproject.toml")
    val childOriginal = "[project]\nname = 'child'\nversion = '0.1.0'\n"
    childManifest.writeText(childOriginal)
    val parent = rootProjectFixture.get()
    val child = childProjectFixture.get()
    rebuildPyProjectModelForTest(projectFixture.get())
    withExecutable(factory) {
      val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
      try {
        assertThat(parent.configureSdkIfNeeded()).isInstanceOf(InterpreterConfigurationResult.Configured::class.java)
        assertThat(child.configureSdkIfNeeded { autoConfigureSdkExistingOnly() })
          .isInstanceOf(InterpreterConfigurationResult.Configured::class.java)

        assertThat(checkNotNull(child.getInterpreter()).detectedHomePath).isEqualTo(root.resolve(".pixi/envs/default"))
        assertThat(child.getInterpreter()).isEqualTo(parent.getInterpreter())
        assertThat(childPath.resolve(".pixi")).doesNotExist()
        assertThat(manifest.readText()).isEqualTo(original)
        assertThat(childManifest.readText()).isEqualTo(childOriginal)
      }
      finally {
        for (pyProject in listOf(child, parent)) {
          for (interpreter in registry.interpreters(pyProject)) registry.removePythonInterpreter(pyProject, interpreter)
        }
      }
    }
  }

  @Test
  fun `an install failure does not register an SDK or configure the module`(factory: PyEnvironmentFactory): Unit =
    timeoutRunBlocking(5.minutes) {
      val manifest = rootFixture.get().resolve("pixi.toml")
      manifest.writeText("[workspace]\nchannels = ['conda-forge']\n[dependencies]\npython = '3.13.*'\n")
      val pyProject = rootProjectFixture.get()
      withExecutable(factory) {
        val proposal = PixiSdkConfiguration().checkEnvironmentAndPrepareSdkCreator(pyProject, emptyList())
        assertThat(proposal).isInstanceOf(CreateInterpreterInfo.WillCreateEnv::class.java)
        val creator = when (proposal) {
          is CreateInterpreterInfoWithInterpreterCreator -> proposal.interpreterCreator
          is CreateInterpreterInfo.WillInstallTool, null -> error("The manifest must offer a Pixi interpreter creator")
        }
        manifest.writeText("[broken\n")

        assertThat(creator.createInterpreter().isFailure).isTrue()
        assertThat(PythonInterpreterRegistry.getInstance(projectFixture.get()).interpreters(pyProject)).isEmpty()
        assertThat(pyProject.getInterpreter()).isNull()
        assertThat(manifest.readText()).isEqualTo("[broken\n")
      }
    }

  private suspend fun withExecutable(factory: PyEnvironmentFactory, action: suspend () -> Unit) {
    val environment = checkNotNull(factory.createEnvironment(environmentSpec).unwrap<PixiPyEnvironment>())
    val tool = PixiPyTool.getInstance()
    val previous = tool.getCustomExecutablePath(localEel.descriptor)
    tool.setCustomExecutablePath(localEel.descriptor, environment.pixiExecutable)
    try {
      action()
    }
    finally {
      tool.setCustomExecutablePath(localEel.descriptor, previous)
    }
  }

  companion object {
    private val environmentSpec = pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
      libraries { +"requests==2.32.3" }
    }
  }
}
