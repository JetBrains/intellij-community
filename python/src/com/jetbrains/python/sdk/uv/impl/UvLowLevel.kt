// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.util.io.delete
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.asKotlinResult
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.sdk.uv.ScriptSyncCheckResult
import com.jetbrains.python.sdk.uv.UvCli
import com.jetbrains.python.sdk.uv.UvLowLevel
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.jetbrains.python.venvReader.tryResolvePath
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

private const val NO_METADATA_MESSAGE = "does not contain a PEP 723 metadata tag"
private const val OUTDATED_ENV_MESSAGE = "The environment is outdated"

private class UvLowLevelImpl(val cwd: Path, private val uvCli: UvCli) : UvLowLevel {
  override suspend fun initializeEnvironment(init: Boolean, python: Path?): PyResult<Path> {
    val addPythonArg: (MutableList<String>) -> Unit = { args ->
      python?.let {
        args.add("--python")
        args.add(python.pathString)
      }
    }

    if (init) {
      val initArgs = mutableListOf("init")
      addPythonArg(initArgs)
      initArgs.add("--no-readme")
      initArgs.add("--no-pin-python")
      initArgs.add("--vcs")
      initArgs.add("none")
      initArgs.add("--no-project")

      uvCli.runUv(cwd, *initArgs.toTypedArray())
        .onFailure { return PyResult.failure(it) }

      // TODO: ask for an uv option not to create
      val hello = cwd.resolve("hello.py").takeIf { it.exists() }
      hello?.delete()

      // called main.py in later versions
      val main = cwd.resolve("main.py").takeIf { it.exists() }
      main?.delete()
    }

    val venvArgs = mutableListOf("venv")
    addPythonArg(venvArgs)
    uvCli.runUv(cwd, *venvArgs.toTypedArray())
      .onFailure { return PyResult.failure(it) }

    if (!init) {
      uvCli.runUv(cwd, "sync")
        .onFailure { return PyResult.failure(it) }
    }

    val path = VirtualEnvReader.Instance.findPythonInPythonRoot(cwd.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME))
    if (path == null) {
      return failure("failed to initialize uv environment")
    }

    return PyResult.success(path)
  }

  override suspend fun listUvPythons(): PyResult<Set<Path>> {
    var out = uvCli.runUv(cwd, "python", "dir")
      .getOr { return it }

    val uvDir = tryResolvePath(out)
    if (uvDir == null) {
      return failure("failed to detect uv python directory")
    }

    // TODO: ask for json output format
    out = uvCli.runUv(cwd, "python", "list", "--only-installed")
      .getOr { return it }

    val pythons = parseUvPythonList(uvDir, out)
    return PyResult.success(pythons)
  }

  override suspend fun listPackages(): PyResult<List<PythonPackage>> {
    val out = uvCli.runUv(cwd, "pip", "list", "--format", "json")
      .getOr { return it }

    data class PackageInfo(val name: String, val version: String)

    val mapper = jacksonObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    val packages = mapper.readValue<List<PackageInfo>>(out).map {
      PythonPackage(it.name, it.version, false)
    }

    return PyExecResult.success(packages)
  }

  override suspend fun listOutdatedPackages(): PyResult<List<PythonOutdatedPackage>> {
    val out = uvCli.runUv(cwd, "pip", "list", "--outdated", "--format", "json")
      .getOr { return it }

    data class OutdatedPackageInfo(val name: String, val version: String, val latest_version: String)

    try {
      val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      val packages = mapper.readValue<List<OutdatedPackageInfo>>(out).map {
        PythonOutdatedPackage(it.name, it.version, it.latest_version)
      }

      return PyExecResult.success(packages)
    }
    catch (e: Exception) {
      return failure(e.message ?: "")
    }
  }

  override suspend fun installPackage(name: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    uvCli.runUv(cwd, "pip", "install", name.formatPackageName(), *options.toTypedArray())
      .onFailure { return PyResult.failure(it) }

    return PyExecResult.success(Unit)
  }

  override suspend fun uninstallPackage(name: PythonPackage): PyResult<Unit> {
    // TODO: check if package is in dependencies and reject it
    uvCli.runUv(cwd, "pip", "uninstall", name.name)
      .onFailure { return PyResult.failure(it) }

    return PyExecResult.success(Unit)
  }

  override suspend fun addDependency(name: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    uvCli.runUv(cwd, "add", name.formatPackageName(), *options.toTypedArray())
      .onFailure { return PyResult.failure(it) }

    return PyExecResult.success(Unit)
  }

  override suspend fun removeDependency(name: PythonPackage): PyResult<Unit> {
    uvCli.runUv(cwd, "remove", name.name)
      .onFailure { return PyResult.failure(it) }

    return PyExecResult.success(Unit)
  }

  override suspend fun isProjectSynced(inexact: Boolean): PyResult<Boolean> {
    val args = constructSyncArgs(inexact)

    uvCli.runUv(cwd, *args.toTypedArray())
      .onFailure {
        val stderr = tryExtractStderr(it)

        if (stderr?.contains(OUTDATED_ENV_MESSAGE) == true) {
          return PyExecResult.success(false)
        }

        return PyExecResult.failure(it)
      }

    return PyExecResult.success(true)
  }

  override suspend fun isScriptSynced(inexact: Boolean, scriptPath: Path): PyResult<ScriptSyncCheckResult> {
    val args = constructSyncArgs(inexact) + listOf("--script", scriptPath.pathString)

    uvCli.runUv(cwd, *args.toTypedArray())
      .onFailure {
        val stderr = tryExtractStderr(it)

        if (stderr?.contains(NO_METADATA_MESSAGE) == true) {
          return PyExecResult.success(ScriptSyncCheckResult.NoInlineMetadata)
        }

        if (stderr?.contains(OUTDATED_ENV_MESSAGE) == true) {
          return PyExecResult.success(ScriptSyncCheckResult.NotSynced)
        }

        return PyExecResult.failure(it)
      }

    return PyExecResult.success(ScriptSyncCheckResult.Synced)
  }

  fun constructSyncArgs(inexact: Boolean): MutableList<String> {
    val args = mutableListOf("sync", "--check")

    if (inexact) {
      args += "--inexact"
    }

    return args
  }

  fun PythonPackageInstallRequest.formatPackageName(): String = when (this) {
    is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecification -> specification.nameWithVersionSpec
    is PythonPackageInstallRequest.AllRequirements -> error("UV supports only single requirement installation")
    is PythonPackageInstallRequest.ByLocation -> error("UV does not support installing from location uri")
  }

  fun parseUvPythonList(uvDir: Path, out: String): Set<Path> {
    val lines = out.lines()
    val pythons = lines.mapNotNull { line ->
      var arrow = line.lastIndexOf("->").takeIf { it > 0 } ?: line.length

      val pythonAndPath = line
        .substring(0, arrow)
        .trim()
        .split(delimiters = arrayOf(" ", "\t"), limit = 2)

      if (pythonAndPath.size != 2) {
        return@mapNotNull null
      }

      val python = tryResolvePath(pythonAndPath[1].trim())
        ?.takeIf { it.exists() && it.startsWith(uvDir) }

      python
    }.toSet()

    return pythons
  }

  override suspend fun sync(): Result<String> {
      return uvCli.runUv(cwd, "sync").asKotlinResult()
  }

  override suspend fun lock(): Result<String> {
      return uvCli.runUv(cwd, "lock").asKotlinResult()
  }
}

fun createUvLowLevel(cwd: Path, uvCli: UvCli = createUvCli()): UvLowLevel {
  return UvLowLevelImpl(cwd, uvCli)
}

private fun tryExtractStderr(err: PyError): String? =
  when (err) {
    is ExecError -> {
      val errorReason = err.errorReason
      when (errorReason) {
        is ExecErrorReason.UnexpectedProcessTermination -> String(errorReason.stderr)
        else -> null
      }
    }
    else -> null
  }