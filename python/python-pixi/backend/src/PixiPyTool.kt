// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.EelFileTransferAttributesStrategy
import com.intellij.platform.eel.provider.utils.EelPathTransfer
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.pytools.backend.PackageManagerPyTool
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.ToolCommandSpec
import com.intellij.python.pytools.backend.ToolSearchPath
import com.intellij.python.pytools.backend.getToolVersion
import com.intellij.python.pytools.backend.pyExecutableSpec
import com.intellij.util.download.DownloadableFileSetDescription
import com.intellij.util.download.eel.EelDownloadableFileService
import com.intellij.util.io.sha256Hex
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.Binary
import com.jetbrains.python.sdk.BinaryPyToolManager
import com.jetbrains.python.sdk.Release
import com.jetbrains.python.sdk.SdksKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

private val LOG = Logger.getInstance(PixiPyTool::class.java)
private const val PIXI_TOOL_NAME = "pixi"

internal class PixiPyTool : PackageManagerPyTool {
  override val packageName: PyPackageName = PyPackageName.from(PIXI_TOOL_NAME)
  override val manager: BinaryPyToolManager = BinaryPyToolManager(
    tool = this,
    releases = SdksKeeper::pixiReleases,
    installBinary = ::installPixiBinary,
  )
  override val toolCommandSpec: ToolCommandSpec = pyExecutableSpec(
    fusId,
    listOf(ToolSearchPath.RelativePathFromHome(listOf(".pixi", "bin"))),
  )

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): PixiPyTool = PyTool.EP_NAME.findExtensionOrFail(PixiPyTool::class.java)
  }
}

private suspend fun installPixiBinary(eel: EelApi, release: Release, binaries: List<Binary>): PyResult<Unit> =
  withContext(Dispatchers.IO) {
    val binary = binaries.singleOrNull()
    if (binary == null) {
      error("The Pixi release ${release.version} has more than one compatible binary")
    }
    val resource = binary.resources.singleOrNull()
    if (resource == null) {
      error("The Pixi release ${release.version} does not have exactly one resource")
    }
    var downloadDirectory: Path? = null
    var stagingFile: Path? = null
    try {
      downloadDirectory = Files.createTempDirectory("pixi-download-")
      val fileService = EelDownloadableFileService.instance
      val fileDescription = fileService.createFileDescription(resource.url.toString(), resource.fileName)
      val fileSet = object : DownloadableFileSetDescription {
        override fun getName(): String = PyPixiBackendBundle.message("python.pixi.download.title")
        override fun getVersionString(): String = release.version
        override fun getFiles() = listOf(fileDescription)
      }
      val downloadedFile = fileService.createDownloader(fileSet).download(downloadDirectory).single().first

      if (resource.sha256 != sha256Hex(downloadedFile)) {
        return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.checksum.failed"))
      }

      val executable = pixiExecutablePath(eel)
      val installDirectory = executable.parent
      Files.createDirectories(installDirectory)
      val executableName = executable.fileName.toString()
      stagingFile = installDirectory.resolve("$executableName.download-${ProcessHandle.current().pid()}-${System.nanoTime()}")

      val attributes = when (eel.platform) {
        is EelPlatform.Posix -> EelFileTransferAttributesStrategy.copyWithRequiredPosixPermissions(PosixFilePermission.OWNER_EXECUTE)
        is EelPlatform.Windows -> EelFileTransferAttributesStrategy.Skip
      }
      EelPathTransfer.walkingTransfer(
        downloadedFile,
        stagingFile,
        removeSource = false,
        fileAttributesStrategy = attributes,
      )

      BinOnEel(stagingFile).getToolVersion(PIXI_TOOL_NAME).getOr { return@withContext it }

      Files.move(stagingFile, executable, StandardCopyOption.REPLACE_EXISTING)
      stagingFile = null
      PyResult.success(Unit)
    }
    catch (e: IOException) {
      PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.install.failed", e.localizedMessage ?: e.javaClass.simpleName))
    }
    finally {
      deleteTemporaryPath(stagingFile)
      deleteTemporaryPath(downloadDirectory)
    }
  }

  private fun deleteTemporaryPath(path: Path?) {
    if (path == null) return

    try {
      EelFileUtils.deleteRecursively(path)
    }
    catch (e: IOException) {
      LOG.warn("Failed to delete the Pixi temporary path: $path", e)
    }
  }

internal fun pixiExecutablePath(eel: EelApi): Path {
  val executableName = when (eel.platform) {
    is EelPlatform.Posix -> "pixi"
    is EelPlatform.Windows -> "pixi.exe"
  }
  return eel.userInfo.home.asNioPath().resolve(".pixi").resolve("bin").resolve(executableName)
}
