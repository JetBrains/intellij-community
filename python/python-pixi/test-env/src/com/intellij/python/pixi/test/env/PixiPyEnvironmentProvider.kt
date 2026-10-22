// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.test.env

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.python.pixi.common.PixiReleaseArchitecture
import com.intellij.python.pixi.common.PixiReleaseOperatingSystem
import com.intellij.python.pixi.common.getPixiReleaseAssetName
import com.intellij.python.test.env.core.GitHubReleaseAssetDownloader
import com.intellij.python.test.env.core.GitHubReleaseChecksumResolver
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.core.PyEnvironmentProvider
import com.intellij.python.test.env.core.executeProcess
import com.intellij.python.test.env.core.markExecutable
import com.intellij.util.system.CpuArch
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.jetbrains.python.PythonBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString

private val LOG = fileLogger()
private val PIXI_RELEASE_DOWNLOADER = GitHubReleaseAssetDownloader(
  repository = "prefix-dev/pixi",
  checksumResolver = GitHubReleaseChecksumResolver.GitHubApiDigest,
)

/** Creates a Pixi project and returns its default Python environment. */
internal class PixiPyEnvironmentProvider : PyEnvironmentProvider<PixiPyEnvironmentSpec>("pixi", PixiPyEnvironmentSpec::class.java) {
  override suspend fun setupEnvironment(context: Context, spec: PixiPyEnvironmentSpec): PyEnvironment = withContext(Dispatchers.IO) {
    val targetPath = nextEnvPath(context.workingDir)
    val pixiExecutable = getOrDownloadPixiExecutable(spec.pixiVersion)
    targetPath.createDirectories()

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
private suspend fun getOrDownloadPixiExecutable(version: String): Path = withContext(Dispatchers.IO) {
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
  val assetName = getPixiReleaseAssetName(os, architecture)
  val tag = "v$version"
  val executable = PIXI_RELEASE_DOWNLOADER.download(tag, assetName)
  markExecutable(LOG, executable)
  executable
}

@OptIn(LowLevelLocalMachineAccess::class)
private fun pixiPythonPath(environmentPath: Path): Path =
  when (OS.CURRENT) {
    OS.Windows -> environmentPath.resolve("python.exe")
    OS.macOS, OS.Linux -> environmentPath.resolve("bin").resolve("python")
    OS.FreeBSD, OS.OHOS, OS.Other -> throw IllegalStateException("Pixi does not support the local operating system: ${OS.CURRENT}")
  }
