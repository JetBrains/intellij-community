// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda.execution

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.osFamily
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ConcurrentProcessWeight
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.ZeroCodeJsonParserTransformer
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.execService.python.advancedApi.validatePythonAndGetInfo
import com.intellij.util.ShellEnvironmentReader
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.sdk.conda.execution.models.CondaEnvInfo
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.runExecutableWithProgress
import com.jetbrains.python.sdk.targetEnvConfiguration
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(IntellijInternalApi::class)
@ApiStatus.Internal
object CondaExecutor {
  suspend fun createNamedEnv(binaryToExec: BinaryToExec, envName: String, pythonVersion: String): PyResult<Unit> {
    val args = listOf("create", "-y", "-n", envName, "python=${pythonVersion}")
    return runConda(
      binaryToExec, args, null
    ) { PyResult.success(Unit) }
  }

  suspend fun createUnnamedEnv(binaryToExec: BinaryToExec, envPrefix: String, pythonVersion: String): PyResult<Unit> {
    val args = listOf("create", "-y", "-p", envPrefix, "python=${pythonVersion}")
    return runConda(
      binaryToExec, args, null
    ) { PyResult.success(Unit) }
  }

  suspend fun createFileEnv(binaryToExec: BinaryToExec, environmentYaml: Path): PyResult<Unit> {
    val args = listOf("env", "create", "-f", environmentYaml.pathString)
    return runConda(
      binaryToExec, args, null
    ) { PyResult.success(Unit) }
  }

  suspend fun updateFromEnvironmentFile(binaryToExec: BinaryToExec, envYmlPath: String, envIdentity: PyCondaEnvIdentity): PyResult<Unit> {
    val args = listOf("env", "update", "--file", envYmlPath, "--prune")
    return runConda(
      binaryToExec, args, envIdentity
    ) { PyResult.success(Unit) }
  }

  suspend fun listEnvs(binaryToExec: BinaryToExec, execService: ExecService = ExecService()): PyResult<CondaEnvInfo> {
    val args = listOf("env", "list", "--json")
    return runConda(
      binaryToExec, args, null,
      execService = execService,
      transformer = ZeroCodeJsonParserTransformer { CondaExecutionParser.parseListEnvironmentsOutput(it) }
    )
  }

  suspend fun exportEnvironmentFile(binaryToExec: BinaryToExec, envIdentity: PyCondaEnvIdentity): PyResult<String> {
    return runConda(
      binaryToExec, listOf("env", "export") + listOf("--no-builds"), envIdentity,
      transformer = ZeroCodeStdoutTransformer
    )
  }

  suspend fun listPackages(binaryToExec: BinaryToExec, envIdentity: PyCondaEnvIdentity): PyResult<List<PythonPackage>> {
    return runConda(
      binaryToExec, listOf("list", "--json"), envIdentity,
      transformer = ZeroCodeJsonParserTransformer { CondaExecutionParser.parseCondaPackageList(it) }
    )
  }

  suspend fun installPackages(binaryToExec: BinaryToExec, envIdentity: PyCondaEnvIdentity, packages: List<String>, options: List<String>): PyResult<Unit> {
    return runConda(
      binaryToExec, listOf("install") + packages + listOf("-y") + options, envIdentity
    ) { PyResult.success(Unit) }

  }

  suspend fun uninstallPackages(binaryToExec: BinaryToExec, envIdentity: PyCondaEnvIdentity, packages: List<String>): PyResult<Unit> {
    return runConda(
      binaryToExec, listOf("uninstall") + packages + "-y", envIdentity
    ) { PyResult.success(Unit) }
  }


  suspend fun listOutdatedPackages(binaryToExec: BinaryToExec, envIdentity: PyCondaEnvIdentity): PyResult<List<PythonOutdatedPackage>> {
    return runConda(
      binaryToExec, listOf("update", "--dry-run", "--all", "--json"), envIdentity,
      transformer = ZeroCodeJsonParserTransformer { CondaExecutionParser.parseOutdatedOutputs(it) }
    )
  }

  suspend fun getPythonInfo(binaryToExec: BinaryToExec, envIdentity: PyCondaEnvIdentity): PyResult<PythonInfo> {
    val runArgs = prepareCondaRunArgs(listOf("run"), listOf("python"), envIdentity)
    return ExecService().validatePythonAndGetInfo(ExecutablePython(binaryToExec, runArgs, emptyMap()))
  }

  private fun prepareCondaRunArgs(
    argsBeforeEnv: List<String>, argsAfterEnv: List<String>, condaEnvIdentity: PyCondaEnvIdentity?,
  ): List<String> {
    val condaEnv = when (condaEnvIdentity) {
      is PyCondaEnvIdentity.UnnamedEnv -> {
        if (condaEnvIdentity.isBase)
          emptyList()
        else
          listOf("-p", condaEnvIdentity.envPath)
      }
      is PyCondaEnvIdentity.NamedEnv -> {
        listOf("-n", condaEnvIdentity.envName)
      }
      null -> emptyList()
    }

    return argsBeforeEnv + condaEnv + argsAfterEnv
  }

  private suspend fun <T> runConda(
    binaryToExec: BinaryToExec,
    args: List<String>,
    condaEnvIdentity: PyCondaEnvIdentity?,
    timeout: Duration = 15.minutes,
    execService: ExecService = ExecService(),
    transformer: ProcessOutputTransformer<T>,
  ): PyResult<T> {
    val envs = getFixedEnvs(binaryToExec).getOr { return it }
    val runArgs = prepareCondaRunArgs(args, emptyList(), condaEnvIdentity).toTypedArray()
    return runExecutableWithProgress(
      binaryToExec,
      timeout,
      env = envs,
      *runArgs,
      transformer = transformer,
      execService = execService,
      processWeight = ConcurrentProcessWeight.HEAVY
    )
  }

  private fun getFixedEnvs(binaryToExec: BinaryToExec): PyResult<Map<String, String>> {
    val pathOnEel = (binaryToExec as? BinOnEel)?.path
                    ?: return PyResult.success(emptyMap())

    val osFamily = pathOnEel.osFamily
    if (!osFamily.isWindows) return PyResult.success(emptyMap())
    if (!pathOnEel.exists()) {
      return PyResult.localizedError(PyBundle.message("python.add.sdk.conda.executable.path.is.not.found"))
    }
    val activateBat = pathOnEel.resolveSibling("activate.bat")
    if (!activateBat.isExecutable()) {
      thisLogger().warn("$activateBat doesn't exist or can't be read")
      return PyResult.success(emptyMap())
    }
    try {
      val command = ShellEnvironmentReader.winShellCommand(activateBat, null)
      val envs = ShellEnvironmentReader.readEnvironment(command, 0).first
      val transformed = transformEnvVars(envs, pathOnEel)
      return PyResult.success(transformed)
    }
    catch (e: IOException) {
      thisLogger().warn("Can't read env vars", e)
      return PyResult.success(emptyMap())
    }
  }

  /**
   * Special fix for conda PATH.
   * conda itself runs base python under the hood. When run non-base env, only non-base env gets activated, so conda
   * first runs non-activated base python. It loads ``sitecustomize.py`` and it leads to mkl loading that throws warning
   * due to path issue (DLL not found).
   * We add this folder to conda path
   */
  private fun transformEnvVars(envs: Map<String, String>, condaPathOnTarget: Path): Map<String, String> {
    val extraPath = condaPathOnTarget.parent?.parent?.resolve("Library")?.resolve("Bin")
    if (extraPath == null || !extraPath.exists() || !extraPath.isDirectory()) {
      thisLogger().warn("$extraPath doesn't exist")
    }
    thisLogger().info("Patching envs")
    return envs.map { (key, value) ->
      val fixedVal = if (key.equals("Path", ignoreCase = true) && extraPath != null) {
        value + ";" + extraPath.absolutePathString()
      }
      else value

      key to fixedVal
    }.toMap()
  }
}


@ApiStatus.Internal
fun Sdk.getCondaBinToExecute(): BinaryToExec {
  val targetConfig = targetEnvConfiguration
  val pathOnTarget = (getOrCreateAdditionalData().flavorAndData.data as PyCondaFlavorData).env.fullCondaPathOnTarget

  val binToExec = when (targetConfig) {
    null -> BinOnEel(Path(pathOnTarget))
    else -> BinOnTarget(pathOnTarget, targetConfig)
  }

  return binToExec
}
