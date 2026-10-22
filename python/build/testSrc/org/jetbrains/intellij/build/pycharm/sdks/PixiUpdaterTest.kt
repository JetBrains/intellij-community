// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm.sdks

import com.intellij.util.Urls
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.sdk.Binary
import com.jetbrains.python.sdk.Product
import com.jetbrains.python.sdk.Release
import com.jetbrains.python.sdk.Resource
import com.jetbrains.python.sdk.ResourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PixiUpdaterTest {
  @Test
  fun `GitHub release metadata produces Pixi SDK binaries`() {
    val release = PixiUpdater { RELEASE_JSON }.getReleases().single()
    val expectedRelease = Release(
      version = "1.2.3",
      product = Product.Pixi,
      sources = null,
      binaries = listOf(
        expectedBinary(OS.macOS, CpuArch.ARM64, "pixi-aarch64-apple-darwin", 1),
        expectedBinary(OS.macOS, CpuArch.X86_64, "pixi-x86_64-apple-darwin", 2),
        expectedBinary(OS.Linux, CpuArch.ARM64, "pixi-aarch64-unknown-linux-musl", 3),
        expectedBinary(OS.Linux, CpuArch.X86_64, "pixi-x86_64-unknown-linux-musl", 4),
        expectedBinary(OS.Windows, CpuArch.ARM64, "pixi-aarch64-pc-windows-msvc.exe", 5),
        expectedBinary(OS.Windows, CpuArch.X86_64, "pixi-x86_64-pc-windows-msvc.exe", 6),
      ),
    )

    assertThat(release).isEqualTo(expectedRelease)
  }

  companion object {
    private fun expectedBinary(os: OS, cpuArch: CpuArch, assetName: String, size: Long): Binary = Binary(
      os = os,
      cpuArch = cpuArch,
      resources = listOf(
        Resource(
          url = Urls.parseEncoded(
            "https://cache-redirector.jetbrains.com/github.com/prefix-dev/pixi/releases/download/v1.2.3/$assetName"
          )!!,
          size = size,
          sha256 = DIGEST.removePrefix("sha256:"),
          fileName = assetName,
          type = ResourceType.STANDALONE_EXECUTABLE,
        )
      ),
    )

    private val RELEASE_JSON = """
      {
        "tag_name": "v1.2.3",
        "assets": [
          { "name": "pixi-aarch64-apple-darwin", "size": 1, "digest": "$DIGEST" },
          { "name": "pixi-x86_64-apple-darwin", "size": 2, "digest": "$DIGEST" },
          { "name": "pixi-aarch64-unknown-linux-musl", "size": 3, "digest": "$DIGEST" },
          { "name": "pixi-x86_64-unknown-linux-musl", "size": 4, "digest": "$DIGEST" },
          { "name": "pixi-aarch64-pc-windows-msvc.exe", "size": 5, "digest": "$DIGEST" },
          { "name": "pixi-x86_64-pc-windows-msvc.exe", "size": 6, "digest": "$DIGEST" }
        ]
      }
    """.trimIndent()
    private const val DIGEST = "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  }
}
