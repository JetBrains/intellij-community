// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.workspace

import com.intellij.execution.Platform
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.execute
import com.intellij.platform.util.annotations.paths.OsPath
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.EelOrTarget
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun <P : PathHolder> pixiFileExists(fileSystem: FileSystem<P>, path: P): PyResult<Boolean> = withContext(Dispatchers.IO) {
  when (val machine = fileSystem.eelOrTarget) {
    is EelOrTarget.IsEel -> PyResult.success(fileSystem.fileExists(path))
    is EelOrTarget.IsTarget -> {
      val probe = pixiTargetFileProbe(machine.target.createEnvironmentRequest(null).targetPlatform.platform, path.toStringOnRemoteMachine())
      ExecService().execute(
        BinOnTarget(probe.executable, machine.target), Args(*probe.arguments.toTypedArray()),
        processOutputTransformer = PIXI_TARGET_FILE_EXISTS,
      )
    }
  }
}

internal data class PixiTargetFileProbe(val executable: String, val arguments: List<String>)

internal fun pixiTargetFileProbe(platform: Platform, path: @OsPath String): PixiTargetFileProbe = when (platform) {
  Platform.UNIX -> PixiTargetFileProbe("test", listOf("-f", path))
  Platform.WINDOWS -> {
    val literal = path.replace("'", "''")
    val command = "try { if (Test-Path -LiteralPath '$literal' -PathType Leaf -ErrorAction Stop) { exit 0 } else { exit 1 } } catch { exit 2 }"
    PixiTargetFileProbe("powershell.exe", listOf("-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command))
  }
}

internal val PIXI_TARGET_FILE_EXISTS: ProcessOutputTransformer<Boolean> = { output ->
  when (output.exitCode) {
    0 -> Result.success(true)
    1 -> Result.success(false)
    else -> Result.failure(null)
  }
}
