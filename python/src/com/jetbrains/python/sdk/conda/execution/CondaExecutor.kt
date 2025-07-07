// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda.execution

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.EnvReader
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.pyRunCatching
import com.jetbrains.python.sdk.conda.execution.models.CondaEnvInfo
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.runExecutableWithProgress
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@ApiStatus.Internal
object CondaExecutor {
  suspend fun createNamedEnv(condaPath: Path, envName: String, pythonVersion: String): PyResult<Unit> {
    val args = listOf("create", "-y", "-n", envName, "python=${pythonVersion}")
    return runConda(condaPath, args, null).mapSuccess { }
  }

  suspend fun createUnnamedEnv(condaPath: Path, envPrefix: String, pythonVersion: String): PyResult<Unit> {
    val args = listOf("create", "-y", "-p", envPrefix, "python=${pythonVersion}")
    return runConda(condaPath, args, null).mapSuccess { }
  }

  suspend fun createFileEnv(condaPath: Path, environmentYaml: Path): PyResult<Unit> {
    val args = listOf("env", "create", "-f", environmentYaml.pathString)
    return runConda(condaPath, args, null).mapSuccess { }
  }

  suspend fun updateFromEnvironmentFile(condaPath: Path, envYmlPath: String, envIdentity: PyCondaEnvIdentity): PyResult<Unit> {
    val args = listOf("env", "update", "--file", envYmlPath, "--prune")
    return runConda(condaPath, args, envIdentity).mapSuccess { }
  }

  suspend fun listEnvs(condaPath: Path): PyResult<CondaEnvInfo> {
    val args = listOf("env", "list", "--json")
    val json = runConda(condaPath, args, null).getOr { return it }
    return pyRunCatching {
      CondaExecutionParser.parseListEnvironmentsOutput(json)
    }
  }

  suspend fun exportEnvironmentFile(condaPath: Path, envIdentity: PyCondaEnvIdentity): PyResult<String> {
    return runConda(condaPath, listOf("env", "export") + listOf("--no-builds"), envIdentity)
  }

  suspend fun listPackages(condaPath: Path, envIdentity: PyCondaEnvIdentity): PyResult<List<PythonPackage>> {
    return runConda(condaPath, listOf("list", "--json"), envIdentity).mapSuccess {
      CondaExecutionParser.parseCondaPackageList(it)
    }
  }

  suspend fun installPackages(condaPath: Path, envIdentity: PyCondaEnvIdentity, packages: List<String>, options: List<String>): PyResult<Unit> {
    return runConda(condaPath, listOf("install") + packages + listOf("-y") + options, envIdentity).mapSuccess { }

  }

  suspend fun uninstallPackages(condaPath: Path, envIdentity: PyCondaEnvIdentity, packages: List<String>): PyResult<Unit> {
    return runConda(condaPath, listOf("uninstall") + packages + "-y", envIdentity).mapSuccess { }
  }


  suspend fun listOutdatedPackages(condaPath: Path, envIdentity: PyCondaEnvIdentity): PyResult<List<PythonOutdatedPackage>> {
    val jsonPyResult = runConda(condaPath, listOf("update", "--dry-run", "--all", "--json"), envIdentity).getOr {
      return it
    }
    return pyRunCatching {
      CondaExecutionParser.parseOutdatedOutputs(jsonPyResult)
    }
  }

  private suspend fun runConda(condaPath: Path, args: List<String>, condaEnvIdentity: PyCondaEnvIdentity?, timeout: Duration = 15.minutes): PyResult<String> {
    val condaEnv = when (condaEnvIdentity) {
      is PyCondaEnvIdentity.UnnamedEnv -> {
        if (condaEnvIdentity.isBase)
          listOf()
        else
          listOf("-p", condaEnvIdentity.envPath)
      }
      is PyCondaEnvIdentity.NamedEnv -> {
        listOf("-n", condaEnvIdentity.envName)
      }
      null -> emptyList()
    }

    val envs = getFixedEnvs(condaPath).getOr {
      return it
    }

    val runArgs = (args + condaEnv).toTypedArray()
    return runExecutableWithProgress(condaPath, null, timeout, env = envs, *runArgs)
  }

  private fun getFixedEnvs(condaPath: Path): PyResult<Map<String, String>> {
    val osFamily = condaPath.getEelDescriptor().osFamily
    if (!osFamily.isWindows) return PyResult.success(emptyMap())
    if (!condaPath.exists()) {
      return PyResult.localizedError(PyBundle.message("python.add.sdk.conda.executable.path.is.not.found"))
    }
    val activateBat = condaPath.resolveSibling("activate.bat")
    if (!activateBat.isExecutable()) {
      thisLogger().warn("$activateBat doesn't exist or can't be read")
      return PyResult.success(emptyMap())
    }
    try {
      val envs = EnvReader().readBatEnv(activateBat, emptyList())
      val transformed = transformEnvVars(envs, condaPath)
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