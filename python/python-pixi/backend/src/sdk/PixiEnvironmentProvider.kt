// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.sdk

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pytools.backend.PyExecutableCache
import com.intellij.python.sdk.backend.PythonEnvironment
import com.intellij.python.sdk.backend.PythonEnvironmentProvider
import com.intellij.python.sdk.backend.ShellActivation
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.ShellType
import com.jetbrains.python.venvReader.VirtualEnvReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

internal class PixiEnvironmentProvider : PythonEnvironmentProvider {
  override val environmentClass: Class<out PythonEnvironment> = PixiEnvironment::class.java

  override fun detect(pythonBinary: Path): PyResult<PythonEnvironment>? {
    val home = VirtualEnvReader().resolvePythonHomeFromPythonBinary(pythonBinary)
    val environments = home.parent ?: return null
    val pixi = environments.parent ?: return null
    if (environments.fileName.toString() != "envs" || pixi.fileName.toString() != ".pixi") return null
    val root = pixi.parent ?: return null
    if (!home.resolve("conda-meta").isDirectory()) return null
    if (!root.resolve("pixi.toml").isRegularFile() && !root.resolve("pyproject.toml").isRegularFile()) return null
    return PyResult.success(PixiEnvironment(pythonBinary, home, root))
  }
}

private class PixiEnvironment(
  override val pythonBinaryPath: Path,
  override val pythonHomePath: Path,
  private val workspace: Path,
) : PythonEnvironment {
  override val version: String? = null
  override val isActivatable: Boolean = true
  override val requiresAssociation: Boolean = true

  override fun activationScript(shellType: ShellType?): ShellActivation? {
    val shell = when (shellType) {
      ShellType.SH, ShellType.BASH -> "bash"
      ShellType.ZSH -> "zsh"
      ShellType.FISH -> "fish"
      ShellType.CMD -> "cmd"
      ShellType.POWERSHELL -> "powershell"
      ShellType.CSH -> return null
      null -> when (pythonBinaryPath.getEelDescriptor().osFamily) {
        EelOsFamily.Windows -> "cmd"
        EelOsFamily.Posix -> "bash"
      }
    }
    val code = runBlockingMaybeCancellable {
      val executable = PyExecutableCache.getInstance().get(pythonBinaryPath.getEelDescriptor(), PixiPyTool.getInstance())
                       ?: return@runBlockingMaybeCancellable null
      ExecService().execGetStdout(
        BinOnEel(executable, workspace.asEelPath()),
        Args("shell-hook", "--as-is", "--shell", shell, "--environment", pythonHomePath.fileName.toString()),
      ).getOrNull()
    } ?: return null
    if (shellType == ShellType.POWERSHELL) return ShellActivation.Snippet(code)
    return try {
      val script = runBlockingMaybeCancellable {
        pythonBinaryPath.getEelDescriptor().toEelApi().fs.createTemporaryFile()
          .prefix("pixi-activation-")
          .suffix(".$shell")
          .deleteOnExit(true)
          .getOrThrowFileSystemException()
          .asNioPath()
      }
      var ready = false
      try {
        EelFiles.writeString(script, code)
        ready = true
        ShellActivation.SourceScript(script)
      }
      finally {
        if (!ready) Files.deleteIfExists(script)
      }
    }
    catch (e: IOException) {
      fileLogger().warn("Cannot create the Pixi activation script", e)
      null
    }
  }
}
