// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.idea.TestFor
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.PixiPyEnvironmentSpec
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pixi.backend.add.PixiSelection
import com.intellij.python.pixi.backend.add.initializePixiWorkspace
import com.intellij.python.pixi.backend.workspace.PixiWorkspace
import com.intellij.python.pytools.backend.getToolVersion
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.core.executeProcess
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.isSuccess
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.apache.tuweni.toml.Toml
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.pathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/** Verifies a pinned Pixi executable and environment. */
@Subsystems.Interpreters
@Layers.Functional
@TestApplication
@TestFor(issues = ["PY-91711"])
@Timeout(value = 11, unit = TimeUnit.MINUTES)
@ExtendWith(PythonFactoryExtension::class)
internal class PixiPyToolEnvTest(private val factory: PyEnvironmentFactory) {
  @Test
  fun `the pinned Pixi executable reports its version`() =
    factory.testWithEnv(environmentSpec) { environment ->
      val executable = environment.pixiExecutable
      val executableVersion = BinOnEel(executable).getToolVersion("pixi").getOrThrow().value

      assertThat(executableVersion).isEqualTo(PIXI_TEST_VERSION)
    }

  @Test
  fun `the Pixi environment provides Python and installs PyPI libraries`() =
    factory.testWithEnv(environmentSpec) { environment ->
      val requestsVersion = ExecService().execGetStdout(
        environment.pythonPath,
        Args("-c", "import requests; print(requests.__version__)"),
      ).getOrThrow().trim()

      assertThat(requestsVersion).isEqualTo("2.32.3")
    }

  @ParameterizedTest
  @CsvSource("false, docs", "true, docs", "false, -docs", "false, default")
  fun `the wizard initializes a named environment without prompts`(
    existingPyproject: Boolean, environmentName: String, @TempDir projectRoot: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val environment = factory.createPixiEnvironment()
    val root = projectRoot.resolve("wizard project").createDirectories()
    val original = """
      # Keep this project comment.
      [project]
      name = "existing-project"
      version = '1.2.3'

      [tool.other]
      enabled = true # Keep this inline comment.

    """.trimIndent()
    if (existingPyproject) root.resolve("pyproject.toml").writeText(original)

    val result = initializePixiWorkspace(
      EelFileSystem(localEel),
      PixiSelection(PathHolder.Eel(environment.pixiExecutable), PixiWorkspace(root, listOf(environmentName)), environmentName, true),
    )

    assertThat(result.isSuccess).isTrue()
    result.getOrThrow()
    val manifest = root.resolve(if (existingPyproject) "pyproject.toml" else "pixi.toml")
    assertThat(Toml.parse(manifest).errors()).isEmpty()
    if (existingPyproject) {
      assertThat(manifest.readText()).contains(original)
      assertThat(root.resolve("pixi.toml")).doesNotExist()
    }
    assertThat(findPixiWorkspace(root)?.environments)
      .containsExactlyElementsOf(if (environmentName == "default") listOf("default") else listOf("default", environmentName))
  }

  @Test
  fun `Pixi adds a named environment without replacing manifest content`(factory: PyEnvironmentFactory): Unit =
    timeoutRunBlocking(5.minutes) {
      val environment = factory.createPixiEnvironment()
      val manifest = environment.envPath.resolve("pixi.toml")
      manifest.appendText("\n# Keep this project comment.\n")

      executeProcess(
        listOf(
          environment.pixiExecutable.pathString,
          "add", "--manifest-path", manifest.pathString, "--feature=docs", "python",
        ),
        thisLogger(),
        "pixi add named feature",
      )
      executeProcess(
        listOf(
          environment.pixiExecutable.pathString,
          "workspace", "environment", "add", "--manifest-path", manifest.pathString, "--feature=docs", "--", "docs",
        ),
        thisLogger(),
        "pixi add named environment",
      )

      assertThat(manifest.readText())
        .contains("# Keep this project comment.")
        .contains("requests = \"==2.32.3\"")
      assertThat(findPixiWorkspace(environment.envPath)?.environments).containsExactly("default", "docs")
    }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `initialization resolves all CLI paths from the process directory`(
    existingPyproject: Boolean, @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val executable = factory.createPixiEnvironment().pixiExecutable
    val ideRoot = temporaryDirectory.resolve("IDE namespace").createDirectories()
    val processRoot = temporaryDirectory.resolve("process namespace").createDirectories()
    val original = "# Keep this comment.\n[project]\nname = 'existing-project'\nversion = '1.0.0'\n"
    if (existingPyproject) {
      ideRoot.resolve("pyproject.toml").writeText(original)
      processRoot.resolve("pyproject.toml").writeText(original)
    }
    val delegate = EelFileSystem(localEel)
    val fileSystem = object : FileSystem<PathHolder.Eel> by delegate {
      override fun getBinaryToExec(path: PathHolder.Eel, workingDir: Path?): BinaryToExec {
        assertThat(workingDir).isEqualTo(ideRoot)
        return delegate.getBinaryToExec(path, processRoot)
      }
    }

    initializePixiWorkspace(
      fileSystem,
      PixiSelection(PathHolder.Eel(executable), PixiWorkspace(ideRoot, listOf("docs")), "docs", true),
    ).getOrThrow()

    assertThat(findPixiWorkspace(processRoot)?.environments).containsExactly("default", "docs")
    assertThat(ideRoot.resolve("pixi.toml")).doesNotExist()
    if (existingPyproject) {
      assertThat(ideRoot.resolve("pyproject.toml").readText()).isEqualTo(original)
      assertThat(processRoot.resolve("pyproject.toml").readText()).contains(original)
    }
  }

  companion object {
    private val environmentSpec = pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
      libraries {
        +"requests==2.32.3"
      }
    }

    private suspend fun PyEnvironmentFactory.createPixiEnvironment(): PixiPyEnvironment {
      val environment = createEnvironment(environmentSpec)
      return requireNotNull(environment.unwrap()) {
        "The Pixi specification did not create a Pixi environment"
      }
    }

    private fun PyEnvironmentFactory.testWithEnv(
      spec: PixiPyEnvironmentSpec,
      timeout: Duration = 10.seconds,
      test: suspend (PixiPyEnvironment) -> Unit,
    ) {
      val environment = timeoutRunBlocking(10.minutes) {
        createEnvironment(spec)
      }
      val pixiEnvironment = environment.unwrap<PixiPyEnvironment>()
      if (pixiEnvironment == null) {
        error("The Pixi specification did not create a Pixi environment")
      }
      timeoutRunBlocking(timeout) {
        test(pixiEnvironment)
      }
    }
  }
}

internal const val PIXI_TEST_VERSION = "0.78.0"
