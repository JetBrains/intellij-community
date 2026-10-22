// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.python.test.env.uv.getUvArchivePrefixToStrip
import com.intellij.python.test.env.uv.getUvInstallationDirectory
import com.intellij.util.system.OS
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@Subsystems.Interpreters
@Layers.Functional
internal class UvExecutableProviderTest {
  @Test
  fun `Windows uv archive is extracted without prefix stripping`() {
    assertThat(getUvArchivePrefixToStrip(OS.Windows, "uv-x86_64-pc-windows-msvc.zip")).isNull()
  }

  @Test
  fun `Unix uv archive is extracted from its root directory`() {
    assertThat(getUvArchivePrefixToStrip(OS.Linux, "uv-x86_64-unknown-linux-gnu.tar.gz"))
      .isEqualTo("uv-x86_64-unknown-linux-gnu")
  }

  @Test
  fun `different uv versions use different installation directories`(@TempDir cacheDirectory: Path) {
    val first = getUvInstallationDirectory(cacheDirectory, "0.8.0", "uv-x86_64-unknown-linux-gnu")
    val second = getUvInstallationDirectory(cacheDirectory, "0.9.0", "uv-x86_64-unknown-linux-gnu")

    assertThat(first).isNotEqualTo(second)
    assertThat(first).isEqualTo(cacheDirectory.resolve("uv/0.8.0/uv-x86_64-unknown-linux-gnu"))
    assertThat(second).isEqualTo(cacheDirectory.resolve("uv/0.9.0/uv-x86_64-unknown-linux-gnu"))
  }
}
