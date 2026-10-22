// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.pytools.backend.PyExecutableCache
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolManager
import com.intellij.python.pytools.backend.PyToolSupport
import com.intellij.python.pytools.backend.PyToolsBundle
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/** Provides the common `sdks.json` workflow for a binary tool. */
@ApiStatus.Internal
@PyInternalExecApi
class BinaryPyToolManager(
  private val tool: PyTool,
  private val releases: () -> List<Release>,
  private val installBinary: suspend (EelApi, Release, List<Binary>) -> PyResult<Unit>,
  private val upgradeBinary: suspend (EelApi, Release, List<Binary>) -> PyResult<Unit> = installBinary,
  private val binarySupport: suspend (EelDescriptor) -> PyToolSupport = { PyToolSupport.INSTALL_AND_UPGRADE },
) : PyToolManager {
  override suspend fun install(eel: EelApi): PyResult<Path> {
    val release = findRelease(eel.platform) ?: return noRelease(tool)
    installBinary(eel, release.release, release.binaries).getOr { return it }
    return resolveInstalledTool(tool, eel)
  }

  override suspend fun upgrade(eel: EelApi): PyResult<Path> {
    val release = findRelease(eel.platform) ?: return noRelease(tool)
    upgradeBinary(eel, release.release, release.binaries).getOr { return it }
    return resolveInstalledTool(tool, eel)
  }

  override suspend fun support(eelDescriptor: EelDescriptor): PyToolSupport {
    val support = binarySupport(eelDescriptor)
    if (!support.canInstall) return support
    return if (findRelease(eelDescriptor.toEelApi().platform) == null) PyToolSupport.NONE else support
  }

  fun findRelease(platform: EelPlatform): BinaryPyToolRelease? {
    val target = platform.binaryTarget() ?: return null
    return releases().firstNotNullOfOrNull { release ->
      val binaries = release.binaries?.filter { it.isCompatible(target.os, target.cpuArch) }.orEmpty()
      if (binaries.isEmpty()) null else BinaryPyToolRelease(release, binaries)
    }
  }

  private fun noRelease(tool: PyTool): PyResult<Path> =
    PyResult.localizedError(PyToolsBundle.message("python.tool.binary.release.not.available", tool.packageName.name))

  private suspend fun resolveInstalledTool(tool: PyTool, eel: EelApi): PyResult<Path> {
    val cache = PyExecutableCache.getInstance()
    cache.invalidate(eel.descriptor, tool)
    return cache.get(eel.descriptor, tool)?.let { PyResult.success(it) }
           ?: PyResult.localizedError(PyToolsBundle.message("python.tool.binary.install.not.detected", tool.packageName.name))
  }
}

@ApiStatus.Internal
@PyInternalExecApi
data class BinaryPyToolRelease(val release: Release, val binaries: List<Binary>)

private data class BinaryTarget(val os: OS, val cpuArch: CpuArch)

private fun EelPlatform.binaryTarget(): BinaryTarget? {
  val os = when (this) {
    is EelPlatform.Darwin -> OS.macOS
    is EelPlatform.Linux -> OS.Linux
    is EelPlatform.Windows -> OS.Windows
    is EelPlatform.FreeBSD, is EelPlatform.OHOS -> return null
  }
  val cpuArch = when (arch) {
    EelPlatform.Arch.ARM_64 -> CpuArch.ARM64
    EelPlatform.Arch.X86_64 -> CpuArch.X86_64
    EelPlatform.Arch.ARM_32 -> CpuArch.ARM32
    EelPlatform.Arch.X86 -> CpuArch.X86
    EelPlatform.Arch.Unknown -> return null
  }
  return BinaryTarget(os, cpuArch)
}
