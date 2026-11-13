// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.test.env

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.python.pixi.common.PixiReleaseArchitecture
import com.intellij.python.pixi.common.PixiReleaseOperatingSystem
import com.intellij.python.pixi.common.getPixiReleaseAssetName
import com.intellij.python.test.env.core.GitHubReleaseAssetDownloader
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.core.PyEnvironmentProvider
import com.intellij.python.test.env.core.Sha256
import com.intellij.python.test.env.core.executeProcess
import com.intellij.python.test.env.core.markExecutable
import com.intellij.python.test.env.core.unpackArchive
import com.intellij.util.io.sha256Hex
import com.intellij.util.system.CpuArch
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.jetbrains.python.PythonBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString

private val LOG = fileLogger()
private val PIXI_CHECKSUM_MANIFEST_SHA256 = mapOf(
  "0.78.0" to pinnedSha256("3e3fc61915c0a60f5ade3cd034d257e60764865d801fea708009bd732f04f1dd"),
  "0.81.0" to pinnedSha256("d6e116dc53fa67af44c44a09f6d19240bf777fb57dd7877329d2f7f84458202a"),
)
private val PIXI_CHECKSUM_SEPARATOR = Regex("\\s+")
private val PIXI_RELEASE_DOWNLOADER = GitHubReleaseAssetDownloader(repository = "prefix-dev/pixi")

private fun pinnedSha256(value: String): Sha256 =
  Sha256.parse(value) ?: error("Invalid pinned Pixi SHA-256 digest")

/** Creates a Pixi project and returns its default Python environment. */
internal class PixiPyEnvironmentProvider : PyEnvironmentProvider<PixiPyEnvironmentSpec>("pixi", PixiPyEnvironmentSpec::class.java) {
  override suspend fun setupEnvironment(context: Context, spec: PixiPyEnvironmentSpec): PyEnvironment = withContext(Dispatchers.IO) {
    val targetPath = nextEnvPath(context.workingDir)
    val pixiExecutable = getOrDownloadPixiExecutable(spec.pixiVersion, targetPath)

    executeProcess(
      listOf(pixiExecutable.pathString, "init", targetPath.pathString, "--channel", "conda-forge"),
      thisLogger(),
      "pixi init",
    )
    val manifestPath = targetPath.resolve("pixi.toml")
    executeProcess(
      listOf(
        pixiExecutable.pathString,
        "add",
        "--manifest-path",
        manifestPath.pathString,
        "python==${spec.pythonVersion.major}.${spec.pythonVersion.minor}.*",
      ),
      thisLogger(),
      "pixi add python",
    )
    if (spec.libraries.isNotEmpty()) {
      executeProcess(
        listOf(pixiExecutable.pathString, "add", "--manifest-path", manifestPath.pathString, "--pypi") + spec.libraries,
        thisLogger(),
        "pixi add PyPI packages",
      )
    }

    val environmentPath = targetPath.resolve(".pixi").resolve("envs").resolve("default")
    val pythonPath = pixiPythonPath(environmentPath)
    if (!pythonPath.exists()) {
      error("Python executable not found in Pixi environment: ${pythonPath.pathString}")
    }
    PixiPyEnvironment(pythonPath, targetPath, pixiExecutable)
  }
}

/** A Python environment and the Pixi executable that created it. */
@ApiStatus.Internal
class PixiPyEnvironment(
  override val pythonPath: PythonBinary,
  override val envPath: Path,
  val pixiExecutable: Path,
) : PyEnvironment {
  @OptIn(ExperimentalPathApi::class)
  override fun close() {
    envPath.deleteRecursively()
  }
}

@OptIn(LowLevelLocalMachineAccess::class)
private suspend fun getOrDownloadPixiExecutable(version: String, targetPath: Path): Path = withContext(Dispatchers.IO) {
  val architecture = when {
    CpuArch.isArm64() -> PixiReleaseArchitecture.ARM_64
    CpuArch.isIntel64() -> PixiReleaseArchitecture.X86_64
    else -> error("Pixi does not support the local CPU architecture")
  }
  val os = when (OS.CURRENT) {
    OS.macOS -> PixiReleaseOperatingSystem.MACOS
    OS.Linux -> PixiReleaseOperatingSystem.LINUX
    OS.Windows -> PixiReleaseOperatingSystem.WINDOWS
    OS.FreeBSD, OS.OHOS, OS.Other -> throw IllegalStateException("Pixi does not support the local operating system: ${OS.CURRENT}")
  }
  val archiveName = getPixiReleaseArchiveName(os, architecture)
  val tag = "v$version"
  val manifestSha256 = PIXI_CHECKSUM_MANIFEST_SHA256[version]
    ?: error(
      "No Pixi $version checksum manifest digest is pinned. " +
      "Add its verified sha256.sum digest to PIXI_CHECKSUM_MANIFEST_SHA256.",
    )
  val checksumManifest = PIXI_RELEASE_DOWNLOADER.download(tag, "sha256.sum")
  val actualManifestSha256 = Sha256.parse(sha256Hex(checksumManifest))
    ?: error("Could not calculate SHA-256 for Pixi $version sha256.sum")
  if (actualManifestSha256 != manifestSha256) {
    error(
      "Pixi $version sha256.sum failed SHA-256 verification (expected ${manifestSha256.value}, got ${actualManifestSha256.value}). " +
      "Verify the release, then update PIXI_CHECKSUM_MANIFEST_SHA256 if the manifest changed.",
    )
  }
  val archiveSha256 = findPixiReleaseAssetSha256(Files.readAllLines(checksumManifest), archiveName)
    ?: error(
      "Pixi $version sha256.sum has no valid SHA-256 entry for $archiveName. " +
      "Check the release asset name and checksum manifest.",
    )
  val archive = PIXI_RELEASE_DOWNLOADER.download(tag, archiveName)
  val actualArchiveSha256 = Sha256.parse(sha256Hex(archive))
    ?: error("Could not calculate SHA-256 for Pixi $version archive $archiveName")
  if (actualArchiveSha256 != archiveSha256) {
    error(
      "Pixi $version archive $archiveName failed SHA-256 verification (expected ${archiveSha256.value}, got ${actualArchiveSha256.value}). " +
      "Check the downloaded file and the verified Pixi release manifest.",
    )
  }
  targetPath.createDirectories()
  val toolDirectory = targetPath.resolve(".pixi-tool")
  toolDirectory.createDirectories()
  unpackArchive(archive, toolDirectory, prefixToStrip = null)
  val executableName = when (os) {
    PixiReleaseOperatingSystem.MACOS, PixiReleaseOperatingSystem.LINUX -> "pixi"
    PixiReleaseOperatingSystem.WINDOWS -> "pixi.exe"
  }
  val executable = toolDirectory.resolve(executableName)
  if (!executable.exists()) {
    error("Pixi executable not found in release archive: ${executable.pathString}")
  }
  markExecutable(LOG, executable)
  executable
}

@ApiStatus.Internal
fun getPixiReleaseArchiveName(
  os: PixiReleaseOperatingSystem,
  architecture: PixiReleaseArchitecture,
): String {
  val executableName = getPixiReleaseAssetName(os, architecture)
  return when (os) {
    PixiReleaseOperatingSystem.MACOS, PixiReleaseOperatingSystem.LINUX -> "$executableName.tar.gz"
    PixiReleaseOperatingSystem.WINDOWS -> "${executableName.removeSuffix(".exe")}.zip"
  }
}

private fun findPixiReleaseAssetSha256(checksumLines: List<String>, assetName: String): Sha256? = checksumLines
  .map { it.trim().split(PIXI_CHECKSUM_SEPARATOR, limit = 2) }
  .firstOrNull { it.size == 2 && it[1].removePrefix("*") == assetName }
  ?.first()
  ?.let { Sha256.parse(it) }

@OptIn(LowLevelLocalMachineAccess::class)
private fun pixiPythonPath(environmentPath: Path): Path =
  when (OS.CURRENT) {
    OS.Windows -> environmentPath.resolve("python.exe")
    OS.macOS, OS.Linux -> environmentPath.resolve("bin").resolve("python")
    OS.FreeBSD, OS.OHOS, OS.Other -> throw IllegalStateException("Pixi does not support the local operating system: ${OS.CURRENT}")
  }
