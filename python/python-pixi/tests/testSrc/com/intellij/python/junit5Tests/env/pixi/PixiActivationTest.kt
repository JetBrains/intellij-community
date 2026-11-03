// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.python.sdk.backend.activationEnvironment
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.intellij.python.sdk.backend.ShellActivation
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import com.jetbrains.python.sdk.ShellType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
@ExtendWith(PythonFactoryExtension::class, LeakedProcessReporterExtension::class)
internal class PixiActivationTest {
  @ParameterizedTest
  @ValueSource(strings = ["default", "docs"])
  fun `process activation applies the selected Pixi environment`(
    environmentName: String, @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit =
    timeoutRunBlocking(5.minutes) {
      val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(PIXI_TEST_VERSION) {
        pythonVersion = pythonVersion("3.13")
      }).unwrap<PixiPyEnvironment>())
      val root = temporaryDirectory.resolve("workspace with spaces").createDirectories()
      val scriptName = if (SystemInfo.isWindows) "activate.bat" else "activate.sh"
      root.resolve(scriptName).writeText(
        if (SystemInfo.isWindows) "@set PIXI_IDE_SCRIPT=from-script\r\n" else "export PIXI_IDE_SCRIPT=from-script\n",
      )
      val manifest = root.resolve("pixi.toml")
      manifest.writeText("""
        [workspace]
        channels = ['conda-forge']
        platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']
        [dependencies]
        python = '3.13.*'
        [activation]
        scripts = ['$scriptName']
        [activation.env]
        PIXI_IDE_ROOT = 'from-root'
        [feature.docs.activation.env]
        PIXI_IDE_FEATURE = 'from-feature'
        [environments]
        docs = ['docs']
      """.trimIndent() + "\n")
      val install = ExecService().execGetStdout(environment.pixiExecutable, Args(
        "install", "--manifest-path", manifest.toString(), "--environment", environmentName,
      ))
      assertThat(install.isSuccess).isTrue()
      val tool = PixiPyTool.getInstance()
      val previous = tool.getCustomExecutablePath(localEel.descriptor)
      tool.setCustomExecutablePath(localEel.descriptor, environment.pixiExecutable)
      try {
        val manifestBefore = manifest.readBytes()
        val lock = root.resolve("pixi.lock")
        val lockBefore = lock.readBytes()
        val home = root.resolve(".pixi/envs/$environmentName")
        val binary = home.resolve(if (SystemInfo.isWindows) "python.exe" else "bin/python")
        val result = binary.activationEnvironment()
        assertThat(result.isSuccess).isTrue()
        val activation = result.getOrThrow()
        assertThat(activation).containsEntry("PIXI_IDE_ROOT", "from-root").containsEntry("PIXI_IDE_SCRIPT", "from-script")
          .containsEntry("PIXI_ENVIRONMENT_NAME", environmentName)
        if (environmentName == "docs") assertThat(activation).containsEntry("PIXI_IDE_FEATURE", "from-feature")
        else assertThat(activation).doesNotContainKey("PIXI_IDE_FEATURE")
        val blockedIdeDirectory = root.resolve(".pixi/ide")
        blockedIdeDirectory.writeText("The workspace is read-only for IDE activation files.")
        val detected = binary.detectPythonEnvironment().getOrThrow()
        val shells = if (SystemInfo.isWindows) listOf(ShellType.CMD, ShellType.POWERSHELL)
        else listOf(ShellType.SH, ShellType.BASH, ShellType.ZSH, ShellType.FISH, ShellType.POWERSHELL)
        for (shell in shells) {
          val shellActivation = checkNotNull(detected.activationScript(shell)) { shell.toString() }
          val code = when (shellActivation) {
            is ShellActivation.SourceScript -> {
              assertThat(shellActivation.scriptPath.startsWith(root)).isFalse()
              assertThat(shellActivation.scriptPath.fileSystem).isEqualTo(binary.fileSystem)
              shellActivation.scriptPath.readText()
            }
            is ShellActivation.Snippet -> shellActivation.code
          }
          assertThat(code).describedAs(shell.toString()).contains("from-root", "PIXI_ENVIRONMENT_NAME")
          if (environmentName == "docs") assertThat(code).contains("from-feature")
          else assertThat(code).doesNotContain("from-feature")
        }
        assertThat(detected.activationScript(ShellType.CSH)).isNull()
        assertThat(blockedIdeDirectory.readText()).isEqualTo("The workspace is read-only for IDE activation files.")
        assertThat(manifest.readBytes()).isEqualTo(manifestBefore)
        assertThat(lock.readBytes()).isEqualTo(lockBefore)
      }
      finally {
        tool.setCustomExecutablePath(localEel.descriptor, previous)
      }
    }
}
