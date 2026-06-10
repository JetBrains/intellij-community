// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.uv

import com.intellij.openapi.diagnostic.Logger
import com.intellij.python.test.env.core.PyEnvDownloadCache
import com.intellij.python.test.env.core.extractIfNecessary
import com.intellij.python.test.env.core.markExecutable
import com.intellij.python.test.env.core.unpackArchive
import com.intellij.util.system.CpuArch
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private const val UV_RELEASES_URL = "https://cache-redirector.jetbrains.com/github.com/astral-sh/uv/releases/download"
private val LOG = Logger.getInstance("com.intellij.python.test.env.uv")

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

  val downloadUrl = getUvDownloadUrl(uvVersion)
  val archiveFileName = downloadUrl.substringAfterLast('/')
  val archiveBaseName = archiveFileName.substringBeforeLast(".tar.gz").substringBeforeLast(".zip")
  val prefixToStrip = getUvArchivePrefixToStrip(OS.CURRENT, archiveFileName)
  val uvDir = PyEnvDownloadCache.cacheDirectory().resolve("uv").resolve(archiveBaseName)
  val uvExecutable = uvDir.resolve(if (OS.CURRENT == OS.Windows) "uv.exe" else "uv")

  extractIfNecessary(uvDir, logger) { target ->
    logger.info("Downloading UV archive: $archiveFileName")
    val cachedArchive = PyEnvDownloadCache.getOrDownload(downloadUrl, archiveFileName)
    logger.info("UV archive downloaded: $cachedArchive")
    logger.info("Extracting UV binary (stripping prefix: $prefixToStrip)")
    unpackArchive(cachedArchive, target, prefixToStrip = prefixToStrip)
    markExecutable(logger, uvExecutable)
  }

  return uvExecutable
}

@ApiStatus.Internal
fun getUvArchivePrefixToStrip(os: OS, archiveFileName: String): String? {
  return if (os == OS.Windows) null else archiveFileName.substringBeforeLast(".tar.gz").substringBeforeLast(".zip")
}

@OptIn(LowLevelLocalMachineAccess::class)
private fun getUvDownloadUrl(version: String): String {
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
    else -> throw IllegalStateException("Unsupported OS: $os")
  }
  val extension = if (os == OS.Windows) "zip" else "tar.gz"

  return "$UV_RELEASES_URL/$version/uv-$platform.$extension"
}
