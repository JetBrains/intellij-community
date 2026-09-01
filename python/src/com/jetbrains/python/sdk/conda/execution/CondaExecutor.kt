// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda.execution

import com.intellij.openapi.projectRoots.Sdk
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
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.getCondaBasePython
import com.intellij.python.sdk.backend.activationEnvironment
import com.jetbrains.python.sdk.conda.execution.models.CondaEnvInfo
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.runExecutableWithProgress
import com.jetbrains.python.sdk.targetEnvConfiguration
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal object CondaExecutor {
  suspend fun createNamedEnv(binaryToExec: BinaryToExec, envName: String, pythonVersion: String): PyResult<Unit> {
    val args = listOf("create", "-y", "-n", envName, "python=${pythonVersion}")
    return runConda(
      binaryToExec, args, null
    ) { PyResult.success(Unit) }
  }

  suspend fun runPythonInCondaEnv(
    binaryToExec: BinaryToExec,
    envIdentity: PyCondaEnvIdentity,
    vararg pythonArgs: String,
  ): PyResult<String> {
    return runConda(
      binaryToExec = binaryToExec,
      args = listOf("run", "--no-capture-output"),
      condaEnvIdentity = envIdentity,
      argsAfterEnv = listOf("python") + pythonArgs,
      transformer = ZeroCodeStdoutTransformer,
    )
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

  suspend fun installPackages(
    binaryToExec: BinaryToExec,
    envIdentity: PyCondaEnvIdentity,
    packages: List<String>,
    options: List<String>,
  ): PyResult<Unit> {
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
    argsAfterEnv: List<String> = emptyList(),
    transformer: ProcessOutputTransformer<T>,
  ): PyResult<T> {
    val envs = condaActivationEnvs(binaryToExec).getOr { return it }
    val runArgs = prepareCondaRunArgs(args, argsAfterEnv, condaEnvIdentity).toTypedArray()
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

  /**
   * conda runs base python under the hood, so the base install's `Library\bin` (MKL DLLs) and the rest of the
   * activation environment must be present (PY-57146). Activate the base conda env through the regular activation
   * pipeline, which also appends `Library\bin` via the conda activation post-processor. Local Windows conda only.
   */
  private suspend fun condaActivationEnvs(binaryToExec: BinaryToExec): PyResult<Map<String, String>> {
    val condaExecutable = (binaryToExec as? BinOnEel)?.path ?: return PyResult.success(emptyMap())
    if (!condaExecutable.osFamily.isWindows) return PyResult.success(emptyMap())
    val baseCondaPython = getCondaBasePython(condaExecutable.pathString) ?: return PyResult.success(emptyMap())
    return Path.of(baseCondaPython).activationEnvironment()
  }
}

internal fun Sdk.getCondaBinToExecute(): BinaryToExec {
  val targetConfig = targetEnvConfiguration
  val pathOnTarget = (pySdkAdditionalData.flavorAndData.data as PyCondaFlavorData).env.fullCondaPathOnTarget

  val binToExec = when (targetConfig) {
    null -> BinOnEel(Path(pathOnTarget))
    else -> BinOnTarget(pathOnTarget, targetConfig)
  }

  return binToExec
}
