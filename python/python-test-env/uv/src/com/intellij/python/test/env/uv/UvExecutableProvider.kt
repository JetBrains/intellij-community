// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.uv

import com.intellij.openapi.diagnostic.Logger
import com.intellij.python.test.env.core.GitHubReleaseAssetDownloader
import com.intellij.python.test.env.core.defaultPyEnvDownloadCache
import com.intellij.python.test.env.core.extractIfNecessary
import com.intellij.python.test.env.core.markExecutable
import com.intellij.python.test.env.core.unpackArchive
import com.intellij.util.system.CpuArch
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val LOG = Logger.getInstance("com.intellij.python.test.env.uv")
private val UV_RELEASE_DOWNLOADER = GitHubReleaseAssetDownloader("astral-sh/uv")

/**
 * Downloads and sets up UV binary for the specified version.
 * If UV is already downloaded for this version, returns the cached executable.
 *
 * @param uvVersion The UV version to download (e.g., "0.5.11")
 * @return Path to the UV executable
 */
@ApiStatus.Internal
@OptIn(LowLevelLocalMachineAccess::class)
suspend fun getOrDownloadUvExecutable(uvVersion: String): Path {
  val logger = LOG

  val archiveFileName = getUvArchiveFileName()
  val archiveBaseName = archiveFileName.substringBeforeLast(".tar.gz").substringBeforeLast(".zip")
  val prefixToStrip = getUvArchivePrefixToStrip(OS.CURRENT, archiveFileName)
  val uvDir = getUvInstallationDirectory(defaultPyEnvDownloadCache.cacheDirectory(), uvVersion, archiveBaseName)
  val uvExecutable = uvDir.resolve(if (OS.CURRENT == OS.Windows) "uv.exe" else "uv")

  extractIfNecessary(uvDir, logger) { target ->
    logger.info("Downloading UV archive: $archiveFileName")
    val cachedArchive = UV_RELEASE_DOWNLOADER.download(uvVersion, archiveFileName)
    logger.info("UV archive downloaded: $cachedArchive")
    logger.info("Extracting UV binary (stripping prefix: $prefixToStrip)")
    unpackArchive(cachedArchive, target, prefixToStrip = prefixToStrip)
    markExecutable(logger, uvExecutable)
  }

  return uvExecutable
}

@ApiStatus.Internal
fun getUvArchivePrefixToStrip(os: OS, archiveFileName: String): String? {
  return when (os) {
    OS.Windows -> null
    OS.macOS, OS.Linux, OS.FreeBSD, OS.OHOS, OS.Other -> archiveFileName.substringBeforeLast(".tar.gz").substringBeforeLast(".zip")
  }
}

@ApiStatus.Internal
fun getUvInstallationDirectory(cacheDirectory: Path, uvVersion: String, archiveBaseName: String): Path =
  cacheDirectory.resolve("uv").resolve(uvVersion).resolve(archiveBaseName)

@OptIn(LowLevelLocalMachineAccess::class)
private fun getUvArchiveFileName(): String {
  val arch = when {
    CpuArch.isArm64() -> "aarch64"
    CpuArch.isIntel64() -> "x86_64"
    else -> "x86_64"
  }

  val os = OS.CURRENT
  val platform = when (os) {
    OS.Windows -> "$arch-pc-windows-msvc"
    OS.macOS -> "$arch-apple-darwin"
    OS.Linux -> "$arch-unknown-linux-gnu"
    OS.FreeBSD, OS.OHOS, OS.Other -> throw IllegalStateException("Unsupported OS: $os")
  }
  val extension = when (os) {
    OS.Windows -> "zip"
    OS.macOS, OS.Linux -> "tar.gz"
    OS.FreeBSD, OS.OHOS, OS.Other -> throw IllegalStateException("Unsupported OS: $os")
  }

  return "uv-$platform.$extension"
}
