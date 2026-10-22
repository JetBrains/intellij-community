// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.common

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class PixiReleaseOperatingSystem {
  MACOS,
  LINUX,
  WINDOWS,
}

@ApiStatus.Internal
enum class PixiReleaseArchitecture(val id: String) {
  ARM_64("aarch64"),
  X86_64("x86_64"),
}

/** Returns the executable asset name published in a Pixi GitHub release. */
@ApiStatus.Internal
fun getPixiReleaseAssetName(
  os: PixiReleaseOperatingSystem,
  architecture: PixiReleaseArchitecture,
): String = when (os) {
  PixiReleaseOperatingSystem.MACOS -> "pixi-${architecture.id}-apple-darwin"
  PixiReleaseOperatingSystem.LINUX -> "pixi-${architecture.id}-unknown-linux-musl"
  PixiReleaseOperatingSystem.WINDOWS -> "pixi-${architecture.id}-pc-windows-msvc.exe"
}
