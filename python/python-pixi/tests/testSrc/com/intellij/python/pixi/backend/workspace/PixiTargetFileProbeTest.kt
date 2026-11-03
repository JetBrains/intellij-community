// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.workspace

import com.intellij.execution.Platform
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.isFailure
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource

@Subsystems.Interpreters
@Layers.Functional
internal class PixiTargetFileProbeTest {
  @ParameterizedTest
  @CsvSource("0, true", "1, false")
  fun `the probe separates a file from a missing path`(exitCode: Int, expected: Boolean) {
    val result = PIXI_TARGET_FILE_EXISTS(EelProcessExecutionResult(exitCode, byteArrayOf(), byteArrayOf()))

    assertThat(result.successOrNull).isEqualTo(expected)
  }

  @ParameterizedTest
  @ValueSource(ints = [2, 127])
  fun `a failed probe cannot report a missing file`(exitCode: Int) {
    val result = PIXI_TARGET_FILE_EXISTS(EelProcessExecutionResult(exitCode, byteArrayOf(), byteArrayOf()))

    assertThat(result.isFailure).isTrue()
  }

  @ParameterizedTest
  @ValueSource(strings = ["/remote project/pixi.toml", "/remote O'Brien/pixi.toml", "/remote/$" + "HOME/pixi.toml"])
  fun `the Unix probe passes the path as one argument`(path: String) {
    val probe = pixiTargetFileProbe(Platform.UNIX, path)

    assertThat(probe.executable).isEqualTo("test")
    assertThat(probe.arguments).containsExactly("-f", path)
  }

  @ParameterizedTest
  @ValueSource(strings = ["C:\\remote project\\pixi.toml", "C:\\remote O'Brien\\pixi.toml", "C:\\remote $" + "HOME\\pixi.toml"])
  fun `the Windows probe uses a PowerShell literal path`(path: String) {
    val probe = pixiTargetFileProbe(Platform.WINDOWS, path)

    assertThat(probe.executable).isEqualTo("powershell.exe")
    assertThat(probe.arguments.take(4)).containsExactly("-NoLogo", "-NoProfile", "-NonInteractive", "-Command")
    assertThat(probe.arguments.last())
      .contains("-LiteralPath '${path.replace("'", "''")}' -PathType Leaf -ErrorAction Stop")
      .contains("{ exit 0 } else { exit 1 }")
      .endsWith("catch { exit 2 }")
  }
}
