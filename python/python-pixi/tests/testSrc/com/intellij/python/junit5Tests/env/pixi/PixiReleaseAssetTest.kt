// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.idea.TestFor
import com.intellij.platform.eel.EelPlatform
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.common.PixiReleaseArchitecture
import com.intellij.python.pixi.common.PixiReleaseOperatingSystem
import com.intellij.python.pixi.common.getPixiReleaseAssetName
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.SdksKeeper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Subsystems.Interpreters
@Layers.Functional
@TestFor(issues = ["PY-91711"])
internal class PixiReleaseAssetTest {
  private val manager = PixiPyTool().manager

  @Test
  fun `release executable name follows the target platform`() {
    val cases = listOf(
      PixiReleaseOperatingSystem.MACOS to "pixi-aarch64-apple-darwin",
      PixiReleaseOperatingSystem.LINUX to "pixi-aarch64-unknown-linux-musl",
      PixiReleaseOperatingSystem.WINDOWS to "pixi-aarch64-pc-windows-msvc.exe",
    )

    assertThat(cases.map { (os, _) -> getPixiReleaseAssetName(os, PixiReleaseArchitecture.ARM_64) })
      .containsExactlyElementsOf(cases.map { (_, expected) -> expected })
  }

  @Test
  fun `SDK metadata selects the executable for each supported platform`() {
    val configuredRelease = SdksKeeper.pixiReleases().single()
    val cases = listOf(
      EelPlatform.Darwin(EelPlatform.Arch.ARM_64) to "pixi-aarch64-apple-darwin",
      EelPlatform.Darwin(EelPlatform.Arch.X86_64) to "pixi-x86_64-apple-darwin",
      EelPlatform.Linux(EelPlatform.Arch.ARM_64) to "pixi-aarch64-unknown-linux-musl",
      EelPlatform.Linux(EelPlatform.Arch.X86_64) to "pixi-x86_64-unknown-linux-musl",
      EelPlatform.Windows(EelPlatform.Arch.ARM_64) to "pixi-aarch64-pc-windows-msvc.exe",
      EelPlatform.Windows(EelPlatform.Arch.X86_64) to "pixi-x86_64-pc-windows-msvc.exe",
    )

    val downloads = cases.map { (platform, _) -> manager.findRelease(platform) }
    assertThat(downloads.map { it?.release }).containsOnly(configuredRelease)
    assertThat(downloads.map { it?.binaries?.singleOrNull()?.resources?.singleOrNull()?.fileName })
      .containsExactlyElementsOf(cases.map { (_, expected) -> expected })
  }

  @Test
  fun `SDK metadata rejects unsupported platforms`() {
    val platforms = listOf(
      EelPlatform.FreeBSD(EelPlatform.Arch.X86_64),
      EelPlatform.OHOS(EelPlatform.Arch.ARM_64),
      EelPlatform.Linux(EelPlatform.Arch.X86),
    )

    assertThat(platforms.map(manager::findRelease)).containsOnlyNulls()
  }
}
