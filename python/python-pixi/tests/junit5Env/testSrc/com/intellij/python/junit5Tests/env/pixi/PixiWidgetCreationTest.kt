// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.junit5Tests.framework.errorSinkFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.evolution.PixiEvoEnvironmentProvider
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.EvoWorkspace
import com.intellij.python.sdk.backend.evolution.EvoRecreateSpec
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.ownedEnvDirOf
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.common.EvoRowAction
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.Compressor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isFailure
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import org.apache.tuweni.toml.Toml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
@ExtendWith(PythonFactoryExtension::class, LeakedProcessReporterExtension::class)
internal class PixiWidgetCreationTest {
  private val rootFixture = tempPathFixture()
  private val projectFixture = projectFixture(rootFixture)
  private val pyProjectFixture = projectFixture.pyProjectFixture(rootFixture)
  private val errorSink = errorSinkFixture()

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `recreate removes external packages and files from only the named environment`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
    }).unwrap<PixiPyEnvironment>())
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val root = rootFixture.get()
    val manifest = root.resolve(manifestName)
    manifest.writeText("""
      [${prefix}workspace]
      channels = ['conda-forge']
      platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']
      [${prefix}dependencies]
      python = '3.12.*'
      [${prefix}feature.docs.dependencies]
      python = '3.13.*'
      [${prefix}environments]
      docs = { features = ['docs'], no-default-feature = true }
    """.trimIndent() + "\n")
    ExecService().execGetStdout(environment.pixiExecutable, Args(
      "install", "--manifest-path", manifest.toString(), "--environment", "docs",
    )).getOrThrow()
    val pyProject = EvoPyProject(pyProjectFixture.get(), null)
    val context = EvoToolContext(
      EvoWorkspace(pyProject, listOf(pyProject)), pyProject, EelFileSystem(localEel), errorSink.get(),
    ) { emptyList() }
    val tool = PixiPyTool.getInstance()
    val previous = tool.getCustomExecutablePath(localEel.descriptor)
    tool.setCustomExecutablePath(localEel.descriptor, environment.pixiExecutable)
    try {
      val provider = PixiEvoEnvironmentProvider()
      val interpreter = provider.createInterpreter(context, provider.interpreterRefOf(PyEnvRef("docs"))).getOrThrow()
      val binary = checkNotNull(interpreter.detectedEnvironment).pythonBinaryPath
      ExecService().execGetStdout(binary, Args("-m", "ensurepip")).getOrThrow()
      val wheel = root.resolve("pixi_recreate_probe-1.0-py3-none-any.whl")
      val metadata = "pixi_recreate_probe-1.0.dist-info"
      val files = mapOf(
        "pixi_recreate_probe.py" to "VALUE = 42\n",
        "$metadata/METADATA" to "Metadata-Version: 2.1\nName: pixi-recreate-probe\nVersion: 1.0\n",
        "$metadata/WHEEL" to "Wheel-Version: 1.0\nRoot-Is-Purelib: true\nTag: py3-none-any\n",
      )
      Compressor.Zip(wheel).use { archive ->
        for ((name, text) in files) archive.addFile(name, text.byteInputStream(), 0)
        val record = (files.keys + "$metadata/RECORD").joinToString("\n", postfix = "\n") { "$it,," }
        archive.addFile("$metadata/RECORD", record.byteInputStream(), 0)
      }
      ExecService().execGetStdout(binary, Args(
        "-m", "pip", "--isolated", "install", "--no-index", "--no-deps", wheel.toString(),
      )).getOrThrow()
      val installedProbe = """
        import importlib.metadata, pixi_recreate_probe
        print(pixi_recreate_probe.VALUE, importlib.metadata.version('pixi-recreate-probe'))
      """.trimIndent()
      assertThat(ExecService().execGetStdout(binary, Args("-c", installedProbe)).getOrThrow().trim()).isEqualTo("42 1.0")
      val untrackedFile = root.resolve(".pixi/envs/docs/untracked.txt")
      untrackedFile.writeText("Remove this file.")
      val sentinels = listOf(
        root.resolve("keep.txt"),
        root.resolve("docs").createDirectories().resolve("keep.txt"),
        root.resolve(".pixi/envs/default").createDirectories().resolve("keep.txt"),
      )
      sentinels.forEach { it.writeText("Keep this file.") }
      val manifestBefore = manifest.readBytes()
      val lock = root.resolve("pixi.lock")
      val lockBefore = lock.readBytes()

      val rebuilt = provider.recreateEnv(context, interpreter.ref, EvoRecreateSpec("manifest", syncPackages = false)).getOrThrow()

      assertThat(rebuilt.ref).isEqualTo(interpreter.ref)
      assertThat(binary.exists()).isTrue()
      assertThat(untrackedFile.exists()).isFalse()
      val removedProbe = """
        import importlib.metadata, importlib.util
        print(importlib.util.find_spec('pixi_recreate_probe') is None)
        print(any(d.metadata['Name'] == 'pixi-recreate-probe' for d in importlib.metadata.distributions()))
      """.trimIndent()
      assertThat(ExecService().execGetStdout(binary, Args("-c", removedProbe)).getOrThrow().trim().lines()).containsExactly("True", "False")
      sentinels.forEach { assertThat(it.readText()).isEqualTo("Keep this file.") }
      assertThat(manifest.readBytes()).isEqualTo(manifestBefore)
      assertThat(lock.readBytes()).isEqualTo(lockBefore)
      if (!SystemInfo.isWindows) {
        val directory = root.resolve(".pixi/envs/docs")
        val parkedDirectory = root.resolve(".pixi/envs/parked")
        Files.move(directory, parkedDirectory)
        Files.createSymbolicLink(directory, root.resolve("docs"))
        try {
          assertThat(provider.recreateEnv(context, interpreter.ref, EvoRecreateSpec("manifest", syncPackages = false)).isFailure).isTrue()
          sentinels.forEach { assertThat(it.readText()).isEqualTo("Keep this file.") }
          assertThat(manifest.readBytes()).isEqualTo(manifestBefore)
          assertThat(lock.readBytes()).isEqualTo(lockBefore)
        }
        finally {
          Files.delete(directory)
          Files.move(parkedDirectory, directory)
        }
      }
    }
    finally {
      tool.setCustomExecutablePath(localEel.descriptor, previous)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `the widget creates an independent named environment and refuses a duplicate`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
    }).unwrap<PixiPyEnvironment>())
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val root = rootFixture.get()
    val manifest = root.resolve(manifestName)
    manifest.writeText("""
      # Keep this workspace comment.
      [${prefix}workspace]
      channels = ['conda-forge']
      platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']
      [${prefix}dependencies]
      python = '3.12.*'
    """.trimIndent() + "\n")
    val pyProject = EvoPyProject(pyProjectFixture.get(), null)
    val context = EvoToolContext(
      EvoWorkspace(pyProject, listOf(pyProject)), pyProject, EelFileSystem(localEel), errorSink.get(),
    ) { emptyList() }
    val tool = PixiPyTool.getInstance()
    val previous = tool.getCustomExecutablePath(localEel.descriptor)
    tool.setCustomExecutablePath(localEel.descriptor, environment.pixiExecutable)
    try {
      val provider = PixiEvoEnvironmentProvider()
      val action = EvoRowAction.CreateEnv("3.13", name = "docs")
      val interpreter = provider.createSdkForNewEnv(context, action).getOrThrow()
      assertThat(interpreter.ref.envRef.value).isEqualTo("docs")
      assertThat(root.resolve(".pixi/envs/docs/conda-meta").exists()).isTrue()
      val text = manifest.readText()
      val table = Toml.parse(text)
      assertThat(table.getString("${prefix}dependencies.python")).isEqualTo("3.12.*")
      assertThat(table.getString("${prefix}feature.docs.dependencies.python")).isEqualTo("3.13.*")
      assertThat(table.getBoolean("${prefix}environments.docs.no-default-feature")).isTrue()
      assertThat(text).contains("# Keep this workspace comment.")
      assertThat(provider.createSdkForNewEnv(context, action).isFailure).isTrue()
      assertThat(manifest.readText()).isEqualTo(text)

      val binary = checkNotNull(interpreter.detectedEnvironment).pythonBinaryPath
      val leaf = provider.evoEnvLeaf("docs", binary, PyEnvRef("docs"))
      assertThat(provider.ownedEnvDirOf(context, leaf.action)).isEqualTo(root.resolve(".pixi/envs/docs"))
      val recreate = provider.recreateSpecFor(context, leaf)
      assertThat(recreate?.options).isNotEmpty()
      binary.deleteExisting()
      val rebuilt = provider.recreateEnv(
        context, interpreter.ref, EvoRecreateSpec(checkNotNull(recreate).options.first().token, syncPackages = false),
      ).getOrThrow()
      assertThat(rebuilt.ref).isEqualTo(interpreter.ref)
      assertThat(binary.exists()).isTrue()
      assertThat(manifest.readText()).isEqualTo(text)
    }
    finally {
      tool.setCustomExecutablePath(localEel.descriptor, previous)
    }
  }
}
