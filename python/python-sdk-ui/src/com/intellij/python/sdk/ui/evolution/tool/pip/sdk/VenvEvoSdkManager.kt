@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.ui.evolution.tool.pip.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdkProvider
import com.intellij.python.sdk.ui.evolution.sdk.parsePythonVersion
import com.intellij.python.sdk.ui.evolution.sdk.resolvePythonExecutable
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.getOrNull
import com.jetbrains.python.resolvePythonHome
import io.github.z4kn4fein.semver.Version
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

@ApiStatus.Internal
suspend fun PythonBinary.getPythonVersion(): Version? {
  val stdout = ExecService().execGetStdout(this, Args("--version")).getOrNull()
  return stdout?.parsePythonVersion()
}


@ApiStatus.Internal
object VenvEvoSdkManager {

  @RequiresBackgroundThread
  suspend fun findEnvironments(module: Module): Result<List<EvoSdk>, PyError> {
    //module.which("pip") ?: return Result.localizedError("pip not found")

    val venvRootPaths = listOf(
      Path.of(module.project.basePath!!)
    )

    val potentialPythonBinaryPaths = venvRootPaths.associateWith { venvRootPath ->
      Files.walk(venvRootPath, 1, FileVisitOption.FOLLOW_LINKS)
        .asSequence()
        .filter { it.isDirectory() }
        .mapNotNull { it.resolvePythonExecutable() }
        .toList()
    }

    val evoSdks = potentialPythonBinaryPaths.flatMap { (k, pythonBinaryPaths) ->
      pythonBinaryPaths.map { buildEvoSdk(it) }
    }.sortedBy { it.pythonBinaryPath }.toList()
    return Result.success(evoSdks)
  }

  fun buildEvoSdk(pythonBinaryPath: Path): EvoSdk {
    val sdkHome = pythonBinaryPath.resolvePythonHome()
    val name = sdkHome.name

    return EvoSdk(
      icon = PythonSdkUIIcons.Tools.Pip,
      name = name,
      pythonBinaryPath = pythonBinaryPath,
    )
  }
}


internal object VenvEvoSdkProvider : EvoSdkProvider {
  override fun parsePySdk(module: Module, sdk: Sdk): EvoSdk? {
    val pythonBinaryPath = sdk.homePath?.let { Path.of(it) } ?: return null
    val evoSdk = VenvEvoSdkManager.buildEvoSdk(pythonBinaryPath)
    return evoSdk
  }
}
